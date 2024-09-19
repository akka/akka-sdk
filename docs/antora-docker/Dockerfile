FROM antora/antora:3.1.9

# "Alpine Linux v3.11"
RUN npm install -g @antora/lunr-extension
RUN npm install -g @antora/site-generator
RUN npm install -g asciidoc-link-check
RUN apk add --update openssh-client git
RUN npm install -g gitlab:antora/xref-validator

ENV NODE_PATH /usr/local/lib/node_modules

LABEL description="antora/antora image with extensions"
