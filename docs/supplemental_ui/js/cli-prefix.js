/* Copy button support for command examples that omit their parent command.
 *
 * A page states its parent command once, in an element marked
 * `.cli-prefix-source`, and writes its examples without that prefix so the
 * words are not repeated down the page. Blocks marked `.cli-prefixed` then
 * copy as a complete, runnable command.
 *
 * The listener is attached to the document in the capture phase, so it runs
 * before the theme's own copy handler on the button and can replace it. That
 * also means this file does not care whether it loads before or after the
 * theme script that creates the buttons.
 */
;(function () {
  'use strict'

  var TRAILING_SPACE = / +$/gm

  document.addEventListener(
    'click',
    function (event) {
      var target = event.target
      if (!target || !target.closest) return

      var button = target.closest('.copy-button')
      if (!button) return

      var block = button.closest('.listingblock.cli-prefixed')
      if (!block) return

      var source = document.querySelector('.cli-prefix-source')
      var prefix = source ? source.textContent.trim() : ''
      if (!prefix) return

      var code = block.querySelector('pre code')
      if (!code || !navigator.clipboard) return

      // Stop the theme's handler, which would copy the block without its prefix.
      event.stopPropagation()
      event.preventDefault()

      var command = prefix + ' ' + code.innerText.replace(TRAILING_SPACE, '')
      navigator.clipboard.writeText(command).then(function () {
        button.classList.add('clicked')
        void button.offsetHeight
        button.classList.remove('clicked')
      })
    },
    true
  )
})()
