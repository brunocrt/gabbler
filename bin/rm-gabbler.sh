#!/usr/bin/env sh

containers=$(docker ps --quiet --filter "name=gabbler")
[[ -n ${containers} ]] && docker rm -f ${containers}
