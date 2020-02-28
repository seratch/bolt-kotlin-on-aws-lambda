package hello

import com.slack.api.bolt.App
import com.slack.api.bolt.AppConfig
import com.slack.api.bolt.response.Responder
import com.slack.api.bolt.service.InstallationService
import com.slack.api.bolt.service.builtin.AmazonS3InstallationService
import com.slack.api.bolt.service.builtin.AmazonS3OAuthStateService
import com.slack.api.model.block.Blocks.*
import com.slack.api.model.block.composition.BlockCompositions.markdownText
import com.slack.api.model.block.composition.BlockCompositions.plainText
import com.slack.api.model.block.element.BlockElements
import com.slack.api.model.block.element.BlockElements.*
import com.slack.api.model.event.AppHomeOpenedEvent
import com.slack.api.model.event.AppMentionEvent
import com.slack.api.model.view.View
import com.slack.api.model.view.Views.*
import java.util.regex.Pattern

class Apps {

    private val installationService: InstallationService =
            AmazonS3InstallationService(System.getenv("SLACK_APP_AMAZON_S3_BUCKET"))

    fun apiApp(): App {
        val app = App()
        app.service(installationService)

        app.event(AppMentionEvent::class.java) { req, ctx ->
            if (req.event.text.contains("ping")) {
                ctx.say("<@${req.event.user}> pong!")
            }
            ctx.ack()
        }

        app.event(AppHomeOpenedEvent::class.java) { req, ctx ->
            val res = ctx.client().viewsPublish { it.userId(req.event.user).view(homeView()) }
            if (!res.isOk) {
                ctx.logger.warn("Failed to update Home tab for user: ${req.event.user}")
            }
            ctx.ack()
        }

        app.blockAction(Pattern.compile("home_button_\\d+")) { req, ctx ->
            ctx.logger.info("payload: ${req.payload}")
            ctx.ack()
        }

        app.command("/make-request") { req, ctx ->
            val privateMetadata = req.payload.responseUrl
            val viewsOpenResult = ctx.client().viewsOpen {
                it.triggerId(req.payload.triggerId).view(modalView(privateMetadata))
            }
            if (viewsOpenResult.isOk) {
                ctx.ack()
            } else {
                ctx.ack(":x: Failed to open a modal (error: ${viewsOpenResult.error})")
            }
        }

        app.viewSubmission("request-modal") { req, ctx ->
            val request = req.payload.view.state.values["request-block"]!!["request-action"]!!.value
            if (request == null || request.trim().isEmpty()) {
                ctx.ackWithErrors(mapOf("request-block" to "write the request here"))
            } else if (request.length < 10) {
                ctx.ackWithErrors(mapOf("request-block" to "must have more than 10 characters"))
            } else {
                val responseUrl = req.payload.view.privateMetadata
                val apiResponse = Responder(ctx.slack, responseUrl).sendToCommand { it.text(request) }
                if (apiResponse.code != 200) {
                    ctx.logger.error("Failed to send a message (error: ${apiResponse.message})")
                }
                ctx.ack()
            }
        }

        return app
    }

    private fun homeView(): View {
        return view { v ->
            v.type("home").blocks(asBlocks(
                    section { s -> s.text(markdownText("*Here's what you can do with Project Tracker:*")) },
                    actions { a ->
                        a.blockId("home_actions").elements(asElements(
                                button { b -> b.actionId("home_button_1").text(plainText("Create New Task")).style("primary").value("create_task") },
                                button { b -> b.actionId("home_button_2").text(plainText("Create New Project")).value("create_project") },
                                button { b -> b.actionId("home_button_3").text(plainText("Help")).value("help") }
                        ))
                    },
                    context { c ->
                        c.elements(asContextElements(
                                BlockElements.image { i -> i.imageUrl("https://api.slack.com/img/blocks/bkb_template_images/placeholder.png").altText("placeholder") }
                        ))
                    },
                    section { s -> s.text(markdownText("*Your Configurations*")) },
                    divider(),
                    section { s -> s.text(markdownText("*#public-relations*\n<fakelink.toUrl.com|PR Strategy 2019> posts new tasks, comments, and project updates to <fakelink.toChannel.com|#public-relations>")).accessory(button { b -> b.text(plainText("Edit")).value("public-relations") }) },
                    divider(),
                    section { s -> s.text(markdownText("*#team-updates*\n<fakelink.toUrl.com|Q4 Team Projects> posts project updates to <fakelink.toChannel.com|#team-updates>")).accessory(button { b -> b.text(plainText("Edit")).value("public-relations") }) },
                    divider(),
                    actions { a ->
                        a.elements(asElements(
                                button { b -> b.actionId("home_button_4").text(plainText("New Configuration")).value("new_configuration") }
                        ))
                    }
            ))
        }
    }

    private fun modalView(privateMetadata: String): View {
        return view {
            it.type("modal")
                    .callbackId("request-modal")
                    .title(viewTitle { t -> t.type("plain_text").text("Request Form") })
                    .submit(viewSubmit { s -> s.type("plain_text").text("Submit") })
                    .close(viewClose { c -> c.type("plain_text").text("Cancel") })
                    .privateMetadata(privateMetadata)
                    .blocks(asBlocks(input { i ->
                        i.blockId("request-block").element(
                                plainTextInput { p ->
                                    p.actionId("request-action").multiline(true)
                                }
                        ).label(plainText { p -> p.text("Detailed Request") })
                    }))
        }
    }

    fun oauthApp(): App {
        val config = AppConfig.builder()
                .scope("app_mentions:read,commands,chat:write")
                .oauthCompletionUrl("https://example.com/thank-you")
                .oauthCancellationUrl("https://example.com/something-wrong")
                .build()
        val app = App(config)
        app.asOAuthApp(true)
        app.service(installationService)
        app.service(AmazonS3OAuthStateService(System.getenv("SLACK_APP_AMAZON_S3_BUCKET")))
        return app
    }
}