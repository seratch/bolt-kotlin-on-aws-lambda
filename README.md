## Bolt for Java (still in beta) runs on AWS Lambda

This is a sample project for building [Bolt Java (still in beta)](https://github.com/slackapi/java-slack-sdk) apps running on AWS Lambda.

On your local machine, I recommend running the app with the [Quarkus](https://quarkus.io/) framework (specifically using the [quarkus-undertow](https://quarkus.io/guides/http-reference)). You'll enjoy all the benefits of [Quarkus's development mode](https://quarkus.io/guides/getting-started). 

Once you make sure if your app works as expected, it's time to deploy the app onto [AWS API Gateway](https://aws.amazon.com/api-gateway/) / [Lambda](https://aws.amazon.com/lambda/)! Just running `./deploy.sh` does everything for you. The shell script internally runs `maven shade:shade` to build a uber jar that runs on the cloud, applies Cloud Formation for preparing all the resources, and then deploy the app as Lambda functions.

## Prerequisites

* [Serverless Framework](https://serverless.com/) (`serverless`, `sls`)
* [Python 3](https://www.python.org/) (`python3`, `pip3`)
* [AWS CLI](https://aws.amazon.com/cli/) (`aws`)
* [OpenJDK 8 or 11](https://openjdk.java.net/install/) (`java`, `javac`)
* [Maven 3](https://maven.apache.org/) (`mvn`)

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

## Slack App Configuration

https://api.slack.com/apps

The "Request URL" would be:
* Local: `https://{your domain}.ngrok.io/slack/events`
* AWS: `https://{your domain].amazonaws.com/${SERVERLESS_STAGE}/slack/events`

The "Redirect URL" would be:
* Local: `https://{your domain}.ngrok.io/slack/oauth/callback`
* AWS: `https://{your domain].amazonaws.com/${SERVERLESS_STAGE}/oauth/callback`

* App Home
  * Turn "Home Tab" On
* Interactive Components
  * Turn "Interactivity" On
  * Set the "Request URL" with the valid "Request URL" above
* Slash Commands
  * Add `/make-request` command with the valid "Request URL" above
* OAuth & Permissions
  * Add a "Redirect URL" with the valid "Redirect URL" above
  * Scopes > Bot Token Scopes
    * app_mentions:read
    * chat:write
    * commands
* Event Subscriptions
  * Turn "Enable Events" On
  * Set the "Request URL" with the valid "Request URL" above
  * Subscribe to bot events
    * app_home_opened
    * app_mention

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