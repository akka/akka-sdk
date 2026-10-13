'use strict'

module.exports = (navUrl, pageUrl) => {
  if (typeof navUrl !== 'string' || typeof pageUrl !== 'string') return false

  // Keep the sidebar open at the listed CLI command group for an unlisted generated subcommand page.
  const commandPage = pageUrl.match(/^(.*\/reference\/cli\/akka-cli\/)(akka_[^/_]+)_[^/]+\.html$/)
  return commandPage !== null && navUrl === `${commandPage[1]}${commandPage[2]}.html`
}
