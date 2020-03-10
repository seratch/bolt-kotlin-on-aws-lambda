package hello.aws

import com.slack.api.bolt.App
import com.slack.api.bolt.aws_lambda.SlackApiLambdaHandler
import com.slack.api.bolt.aws_lambda.request.ApiGatewayRequest
import hello.Apps

class SlackOAuthHandler(app: App?) : SlackApiLambdaHandler(app) {
  constructor() : this(Apps().oauthApp())

  override fun isWarmupRequest(awsReq: ApiGatewayRequest?): Boolean {
    return awsReq != null
      && awsReq.body != null
      && awsReq.body == "warmup=true"
  }
}
