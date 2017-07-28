package api

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, ExceptionHandler}
import services.EntityNotFoundException

trait CustomExceptionHandling {
  import Directives._

  implicit def customExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case EntityNotFoundException(msg) => {
        complete(HttpResponse(StatusCodes.NotFound, entity = msg))
      }
    }
}

object CustomExceptionHandling extends CustomExceptionHandling
