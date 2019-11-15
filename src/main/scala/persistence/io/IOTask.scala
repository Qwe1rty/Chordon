package persistence.io

import akka.actor.ActorRef
import better.files.File

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}


sealed trait IOTask {

  def execute()(implicit executionContext: ExecutionContext): Unit
}


case class ReadTask(valueFile: File)(implicit stateActor: ActorRef) extends IOTask {

  override def execute()(implicit executionContext: ExecutionContext): Unit = {

    stateActor ! ReadCompleteSignal(Try(valueFile.loadBytes))
  }
}

case class WriteAheadTask(writeAheadFile: File, value: Array[Byte])(implicit stateActor: ActorRef) extends IOTask {

  override def execute()(implicit executionContext: ExecutionContext): Unit = {

    val result = Try(writeAheadFile.writeByteArray(value))
    stateActor ! (
      result match {
        case Success(_) => WriteAheadCommitSignal
        case Failure(e: Exception) => WriteAheadFailureSignal(e)
      }
    )
  }
}

case class WriteTransferTask(writeAheadFile: File, valueFile: File)(implicit stateActor: ActorRef) extends IOTask {

  override def execute()(implicit executionContext: ExecutionContext): Unit = {

    ???
  }
}

case class TombstoneTask(valueFile: File)(implicit stateActor: ActorRef) extends IOTask {

  override def execute()(implicit executionContext: ExecutionContext): Unit = {

    ??? // TODO figure best way to tombstone
  }
}