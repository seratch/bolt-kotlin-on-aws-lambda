package hello.aws

import com.amazonaws.regions.Regions
import com.amazonaws.services.lambda.AWSLambdaClient
import com.amazonaws.services.lambda.model.InvokeRequest
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.slack.api.bolt.aws_lambda.request.ApiGatewayRequest
import com.slack.api.bolt.aws_lambda.response.ApiGatewayResponse

class WarmupHandler : RequestHandler<ApiGatewayRequest, ApiGatewayResponse> {

  private val service = System.getenv("SERVERLESS_SERVICE")
  private val stage = System.getenv("SERVERLESS_STAGE")
  private val payload = """{"body":"warmup=true"}"""
  private val functionNames = listOf("$service-$stage-api", "$service-$stage-oauth")

  override fun handleRequest(req: ApiGatewayRequest?, context: Context?): ApiGatewayResponse {
    val lambda = AWSLambdaClient.builder().withRegion(Regions.AP_NORTHEAST_1).build()
    for (functionName in functionNames) {
      val invokeRequest = InvokeRequest().withFunctionName(functionName).withPayload(payload)
      val result = lambda.invoke(invokeRequest)
      if (result.statusCode != 200 || result.functionError != null) {
        throw RuntimeException("status: ${result.statusCode} function error: ${result.functionError}")
      }
    }
    return ApiGatewayResponse.builder().statusCode(200).rawBody("done").build()
  }
}