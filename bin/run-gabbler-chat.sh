#!/usr/bin/env sh

n=0
if [ -n "$1" ]; then
    n="$1"
fi

tag=latest
if [ -n "$2" ]; then
    tag="$2"
fi

: ${HOST:=$(ipconfig getifaddr en0)}
: ${HOST:=$(ipconfig getifaddr en1)}
: ${HOST:=$(ipconfig getifaddr en2)}
: ${HOST:=$(ipconfig getifaddr en3)}
: ${HOST:=$(ipconfig getifaddr en4)}

docker run \
  --detach \
  --name gabbler-chat-${n} \
  --publish 801${n}:8000 \
  hseeberger/gabbler-chat:${tag} \
  -Dcassandra-journal.contact-points.0=${HOST}:9042 \
  -Dconstructr.coordination.host=${HOST} \
  -Dgabbler-chat.user-repository.user-events=http://${HOST}:8000/user-events
