#!/usr/bin/env ruby
# Flags same-page anchor links (<<id>>, <<id,text>>, or <<Section Title>>)
# that do not resolve to an anchor on the page. The Antora build neither
# fails nor warns on these; the link renders pointing at an id that does not
# exist and is dead in the browser.
#
# Each page is converted with Asciidoctor and its rendered HTML inspected:
# every href="#id" must match an id="..." emitted on the same page. This
# relies on Asciidoctor's own id generation and natural-xref matching rather
# than reproducing those rules.
#
# Cross-page xref:page.adoc#id[] targets are out of scope: the Antora build
# already validates and fails on those. Antora-specific include targets
# (example$..., ROOT:partial$...) are not resolvable outside an Antora build
# and are skipped, so only anchors defined in page content are checked.

require 'asciidoctor'
require 'find'
require 'set'

Asciidoctor::LoggerManager.logger = Asciidoctor::NullLogger.new

docs_root = File.expand_path('../src', __dir__)
errors = []

Find.find(docs_root) do |path|
  next unless path.end_with?('.adoc')
  next if path.include?('/partials/')

  doc = Asciidoctor.load_file(path, safe: :unsafe, catalog_assets: false)
  html = doc.convert

  ids = html.scan(/\bid="([^"]+)"/).flatten.to_set
  hrefs = html.scan(/<a\s[^>]*href="#([^"]+)"/).flatten.uniq

  hrefs.each do |id|
    next if ids.include?(id)

    errors << "#{path}: link to \"##{id}\" does not resolve to any anchor on this page"
  end
end

if errors.empty?
  puts 'All internal anchor links resolve.'
else
  errors.each { |e| warn e }
  warn "\n#{errors.size} dangling internal reference(s) found."
  exit 1
end
