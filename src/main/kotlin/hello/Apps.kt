package hello

import com.slack.api.app_backend.interactive_components.response.Option
import com.slack.api.bolt.App
import com.slack.api.bolt.AppConfig
import com.slack.api.bolt.response.Responder
import com.slack.api.bolt.service.InstallationService
import com.slack.api.bolt.service.builtin.AmazonS3InstallationService
import com.slack.api.bolt.service.builtin.AmazonS3OAuthStateService
import com.slack.api.model.Action
import com.slack.api.model.Attachments.*
import com.slack.api.model.block.Blocks.*
import com.slack.api.model.block.composition.BlockCompositions.markdownText
import com.slack.api.model.block.composition.BlockCompositions.plainText
import com.slack.api.model.block.element.BlockElements
import com.slack.api.model.block.element.BlockElements.*
import com.slack.api.model.event.AppHomeOpenedEvent
import com.slack.api.model.event.AppMentionEvent
import com.slack.api.model.view.View
import com.slack.api.model.view.Views.*
import java.net.URLDecoder
import java.time.ZonedDateTime
import java.util.*
import java.util.regex.Pattern
import com.slack.api.app_backend.dialogs.response.Option as DialogOption

class Apps {

    private val installationService: InstallationService =
            AmazonS3InstallationService(System.getenv("SLACK_APP_AMAZON_S3_BUCKET"))

    fun apiApp(): App {
        val app = App()
        app.service(installationService)

        // -------------------------------
        // Middleware
        // -------------------------------

        app.use { req, _, chain ->
            val body = req.requestBodyAsString
            val payload = if (body.startsWith("payload=")) {
                URLDecoder.decode(body.split("payload=")[1], "UTF-8")
            } else body
            req.context.logger.info(payload)
            chain.next(req)
        }

        // -------------------------------
        // Slash Commands
        // -------------------------------

        app.command("/time") { req, ctx ->
            val input = req.payload.text
            val tz = if (input == null || input.isEmpty()) TimeZone.getDefault() else
                TimeZone.getTimeZone(req.payload.text)
            val reply = ZonedDateTime.now(tz.toZoneId()).toString()
            ctx.ack { it.text(reply) }
        }

        // -------------------------------
        // Events API
        // -------------------------------

        app.event(AppMentionEvent::class.java) { req, ctx ->
            if (req.event.text.contains("ping")) {
                ctx.say("<@${req.event.user}> pong!")
            }
            ctx.ack()
        }

        // -------------------------------
        // Home tab
        // -------------------------------

        app.event(AppHomeOpenedEvent::class.java) { req, ctx ->
            val res = ctx.client().viewsPublish {
                it.userId(req.event.user).view(homeView())
            }
            if (!res.isOk) ctx.logger.warn("Failed to update Home tab for user: ${req.event.user}")
            ctx.ack()
        }

        app.blockAction(Pattern.compile("home_button_\\d+")) { req, ctx ->
            ctx.logger.info("payload: ${req.payload}")
            val res = ctx.client().viewsOpen { r ->
                r.triggerId(req.payload.triggerId).view(view { v ->
                    v.callbackId("home_button_click_event")
                            .type("modal")
                            .title(viewTitle { t -> t.type("plain_text").text("Click Event") })
                            .close(viewClose { c -> c.type("plain_text").text("Close") })
                            .blocks(asBlocks(section { s -> s.text(plainText("You clicked a button!")) }))
                            .notifyOnClose(true)
                })
            }
            if (!res.isOk) ctx.logger.info(res.toString())
            ctx.ack()
        }
        app.viewClosed("home_button_click_event") { _, ctx ->
            ctx.ack()
        }

        // -------------------------------
        // Modals
        // -------------------------------

        app.command("/test-modal") { req, ctx ->
            val privateMetadata = req.payload.responseUrl
            val viewsOpenResult = ctx.client().viewsOpen {
                it.triggerId(req.payload.triggerId).view(modalView(privateMetadata))
            }
            if (viewsOpenResult.isOk) {
                ctx.ack(asBlocks(section {
                    it.text(markdownText("test"))
                            .accessory(externalSelect { s ->
                                s.actionId("select-action").minQueryLength(0)
                            })
                }))
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

        val allOptions: List<Option> = listOf(
                Option(plainText("Schedule", true), "schedule"),
                Option(plainText("Budget", true), "budget"),
                Option(plainText("Assignment", true), "assignment")
        )
        app.blockSuggestion("select-action") { _, ctx ->
            ctx.ack { it.options(allOptions) }
        }

        app.blockAction("select-action") { req, ctx ->
            ctx.respond("You chose ${req.payload.actions[0].selectedOption}!")
            ctx.ack()
        }

        app.viewClosed("request-modal") { _, ctx ->
            ctx.ack()
        }

        // -------------------------------
        // Message Action
        // -------------------------------

        app.messageAction("test-message-action") { _, ctx ->
            ctx.respond("Thanks!")
            ctx.ack()
        }

        // -------------------------------
        // Dialogs
        // -------------------------------

        app.command("/test-dialog") { req, ctx ->
            val result = ctx.client().dialogOpen {
                it.triggerId(req.payload.triggerId).dialogAsString("""
                {
                  "callback_id": "dialog-test",
                  "title": "Request a Ride",
                  "submit_label": "Request",
                  "notify_on_cancel": true,
                  "state": "Limo",
                  "elements": [
                    {
                      "type": "text",
                      "label": "Pickup Location",
                      "name": "loc_origin"
                    },
                    {
                      "type": "text",
                      "label": "Dropoff Location",
                      "name": "loc_destination"
                    },
                    {
                      "type": "select",
                      "label": "Price",
                      "name": "price_list",
                      "data_source": "external",
                      "min_query_length": 1
                    }
                  ]
                }
            """.trimIndent())
            }
            if (result.isOk) ctx.ack()
            else ctx.ack(":x: Failed to open a dialog (error: ${result.error})")
        }

        app.dialogCancellation("dialog-test") { req, ctx ->
            ctx.respond { it.text("```${req.payload}```") }
            ctx.ack()
        }
        app.dialogSubmission("dialog-test") { req, ctx ->
            ctx.respond { it.text("```${req.payload.submission}```") }
            ctx.ack()
        }
        app.dialogSuggestion("dialog-test") { _, ctx ->
            val options = listOf(
                    DialogOption.builder().label("Premium").value("prm").build(),
                    DialogOption.builder().label("Standard").value("std").build()
            )
            ctx.ack { it.options(options) }
        }

        // -------------------------------
        // Attachments
        // -------------------------------
        app.command("/test-attachments") { _, ctx ->
            val attachments = listOf(
                    attachment {
                        it.text("Choose a game to play")
                                .fallback("You are unable to choose a game")
                                .callbackId("wopr_game")
                                .color("#3AA3E3")
                                .actions(asActions(
                                        action { a -> a.name("game").text("Chess").type(Action.Type.BUTTON).value("chess") },
                                        action { a -> a.name("game").text("Falken's Maze").type(Action.Type.BUTTON).value("maze") },
                                        action { a ->
                                            a.name("game")
                                                    .text("Thermonuclear War")
                                                    .type(Action.Type.BUTTON)
                                                    .style("danger")
                                                    .value("war")
                                                    .confirm(confirm { c ->
                                                        c.title("Are you sure?")
                                                                .text("Wouldn't you prefer a good game of chess?")
                                                                .okText("Yes")
                                                                .dismissText("No")
                                                    })
                                        }
                                ))
                    }
            )
            ctx.ack { it.attachments(attachments) }
        }

        app.attachmentAction("wopr_game") { req, ctx ->
            ctx.respond("Sent actions: ${req.payload.actions}")
            ctx.ack()
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
                    .notifyOnClose(true)
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
                .scope("commands,app_mentions:read,chat:write,chat:write.public")
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