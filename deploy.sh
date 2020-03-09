#!/bin/bash -x

if [ "${SLACK_APP_CLIENT_ID}" == "" ]; then
  echo "Visit https://api.slack.com/apps and set SLACK_APP_CLIENT_ID=xxxx"
  exit 1
fi
if [ "${SLACK_APP_CLIENT_SECRET}" == "" ]; then
  echo "Visit https://api.slack.com/apps and set SLACK_APP_CLIENT_SECRET=xxxx"
  exit 1
fi
if [ "${SLACK_SIGNING_SECRET}" == "" ]; then
  echo "Visit https://api.slack.com/apps and set SLACK_SIGNING_SECRET=xxxx"
  exit 1
fi
if [ "${SLACK_APP_REDIRECT_URI}" == "" ]; then
  echo "Visit https://api.slack.com/apps and set SLACK_APP_REDIRECT_URI=xxxx"
  exit 1
fi
if [ "${SERVERLESS_STAGE}" == "" ]; then
  export SERVERLESS_STAGE=dev
fi

export SLS_DEBUG=*

mvn -f pom_aws.xml clean package shade:shade &&
  ./node_modules/serverless/bin/serverless deploy --stage ${SERVERLESS_STAGE} -v &&
  ./node_modules/serverless/bin/serverless invoke --stage ${SERVERLESS_STAGE} --function warmup
