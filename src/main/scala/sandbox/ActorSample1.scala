package sandbox

import akka.actor.{ActorSystem, Actor, Props}
import akka.pattern.ask  // for '?' operator
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps  // for suppress warning

object ActorSample1 {
  
  /**
   * Word count sample
   * 
   * e.g.
   *   [worker-1] I have completed a task. ('I'm 21 years old.' = 4)
   *   [worker-2] I have completed a task. ('Scala is an acronym for “Scalable Language”.' = 7)
   *   [worker-2] I have completed a task. ('Build powerful concurrent & distributed applications more easily.' = 8)
   *   [worker-1] I have completed a task. ('This pen is my banana.' = 5)
   *   [worker-1] I have completed a task. ('singleword' = 1)
   *   [worker-2] Thank you.
   *   [worker-1] Thank you.
   *   TaskResult(Task(singleword),1)
   *   TaskResult(Task(I'm 21 years old.),4)
   *   TaskResult(Task(This pen is my banana.),5)
   *   TaskResult(Task(Scala is an acronym for “Scalable Language”.),7)
   *   TaskResult(Task(Build powerful concurrent & distributed applications more easily.),8)
   */
  def main(args: Array[String]) {
    val MaxWorkers = 2
    implicit val AskTimeout = Timeout(3 seconds)  // timeout of asking to actors

    import WordCount._
    
    // tasks
    val tasks = Seq(
      Task("I'm 24 years old."),
      Task("This pen is my banana."),
      Task("singleword"),
      Task("Scala is an acronym for “Scalable Language”."),
      Task("Build powerful concurrent & distributed applications more easily.")
    )
    
    // initialize actor system and workers(actors)
    val system = ActorSystem("akka-sandbox")
    val workers = (1 to MaxWorkers) map { i =>
      system.actorOf(Props[Worker], s"worker-$i")
    }
    
    // assign tasks to workers
    tasks.zipWithIndex foreach { case (task, idx) =>
      workers(idx % workers.length) ! task
    }
    
    // receive results from workers
    val taskResults = workers map { worker =>
      Await.result(
        ask(worker, Finish).mapTo[List[TaskResult]],
        AskTimeout.duration)
    } flatten
    
    taskResults.sortBy(_.count) foreach println  // display task results
    
    system.shutdown()  // shutdown after task finished
  }
}

package WordCount {
  case class  Task(sentence: String)
  case class  TaskResult(task: Task, count: Int)
  case object Finish
  
  class Worker extends Actor {
    import scala.collection.mutable.ListBuffer
    private val completed = new ListBuffer[TaskResult]
    
    def receive = {
      case Task(sentence) => {
        val count = sentence.trim().split(" ").length  // count word of sentence
        completed += TaskResult(Task(sentence), count)
        println(s"[${self.path.name}] I have completed a task. ('$sentence' = $count)")
      }
      case Finish => {
        sender ! completed.toList  // reply to main
        completed.clear()
        println(s"[${self.path.name}] Thank you.")
      }
    }
  }
}
