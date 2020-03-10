package hello.quarkus

import com.slack.api.bolt.App
import com.slack.api.bolt.servlet.SlackAppServlet
import com.slack.api.bolt.servlet.SlackOAuthAppServlet
import hello.Apps
import javax.servlet.annotation.WebServlet

@WebServlet("/slack/events")
class ApiAppServlet(app: App?) : SlackAppServlet(app) {
  constructor() : this(Apps().apiApp())
}

@WebServlet("/slack/oauth/*")
class OAuthAppServlet(app: App?) : SlackOAuthAppServlet(app) {
  constructor() : this(Apps().oauthApp())
}