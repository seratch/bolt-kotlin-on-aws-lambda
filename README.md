## Bolt for Java runs on AWS Lambda

This is a sample project for building [Bolt for Java](https://github.com/slackapi/java-slack-sdk) apps running on AWS Lambda.

On your local machine, I recommend running the app with the [Quarkus](https://quarkus.io/) framework (specifically using the [quarkus-undertow](https://quarkus.io/guides/http-reference)). You'll enjoy all the benefits of [Quarkus's development mode](https://quarkus.io/guides/getting-started). 

Once you make sure if your app works as expected, it's time to deploy the app onto [AWS API Gateway](https://aws.amazon.com/api-gateway/) / [Lambda](https://aws.amazon.com/lambda/)! Just running `./deploy.sh` does everything for you. The shell script internally runs `maven shade:shade` to build a uber jar that runs on the cloud, applies Cloud Formation for preparing all the resources, and then deploys the app as Lambda functions.

## Required Tools

* [OpenJDK 8 or 11](https://openjdk.java.net/install/) (`java`, `javac`)
  * [Maven 3](https://maven.apache.org/) (`mvn`)
* [Serverless Framework](https://serverless.com/) (`serverless`, `sls`)
  * [AWS CLI](https://aws.amazon.com/cli/) (`aws`)
  * [Python 3](https://www.python.org/) (`python3`, `pip3`)

```
# install JDK, Maven (mvn command)
brew install openjdk@11
brew install maven

# install serverless command
npm i

# AWS CLI
curl -O https://bootstrap.pypa.io/get-pip.py
python3 get-pip.py --user
pip3 install awscli --upgrade --user
aws configure
```

## AWS Resources

The only thing you need to manually do is to create an S3 bucket for storing OAuth state values and bot/user tokens. Needless to say, please be careful with its access permissions.

```
export SLACK_APP_AMAZON_S3_BUCKET=your-own-unique-bucket-name
```

Apart from it, everything will be done by Serverless Framework's Cloud Formation tasks!

## Slack App Configuration

Configure the followings [here](https://api.slack.com/apps).

* **App Home**
  * Turn "Home Tab" On
* **Interactive Components**
  * Turn "Interactivity" On
  * Set the "Request URL" with the valid "Request URL" below
  * Add shortcuts
    * Global shortcut - Callback ID: `test-global-shortcut`
    * Message shortcut - Callback ID: `test-message-action`
  * Set the valid "Request URL" below for Select Menus's Options Load URL
* **Slash Commands**
  * Add `/time`, `/test-modal`, `/test-dialog`, `/test-attachments` with the valid "Request URL" below
* **OAuth & Permissions**
  * Add a "Redirect URL" with the valid "Redirect URL" below
  * Scopes > Bot Token Scopes
    * app_mentions:read
    * chat:write
    * chat:write.public
    * commands
* **Event Subscriptions**
  * Turn "Enable Events" On
  * Set the "Request URL" with the valid "Request URL" below
  * Subscribe to bot events
    * `app_home_opened`
    * `app_mention`

The "Request URL" would be:
* Local: `https://{your domain}.ngrok.io/slack/events`
* AWS: `https://{your domain].amazonaws.com/${SERVERLESS_STAGE}/slack/events`

The "Redirect URL" would be:
* Local: `https://{your domain}.ngrok.io/slack/oauth/callback`
* AWS: `https://{your domain].amazonaws.com/${SERVERLESS_STAGE}/oauth/callback`


## Local Development

```bash
cp -p _env .env_dev # and then modify it
source .env_dev
mvn quarkus:dev # localhost:3000
```

```bash
ngrok http 3000 --subdomain {your domain here}
```

Access `https://{your domain}.ngrok.io/slack/oauth/start` in your web browser to install the app to a workspace.

## deployment

```bash
cp -p _env_aws .env_aws_prod
source .env_aws_prod
./deploy.sh
```
