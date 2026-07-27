FROM antora/antora:3.1.15

# "Alpine Linux v3.11"
RUN npm install -g @antora/lunr-extension
RUN npm install -g @antora/site-generator
RUN npm install -g asciidoc-link-check
RUN npm install -g @asciidoctor/tabs@1.0.0-beta.6

ENV NODE_PATH=/usr/local/lib/node_modules

LABEL description="antora/antora image with extensions"
