package modules

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import services.ImportService

class FootbalistoModule extends AbstractModule with AkkaGuiceSupport {

  override def configure = {
    bind[ImportService](classOf[ImportService])
  }
}
