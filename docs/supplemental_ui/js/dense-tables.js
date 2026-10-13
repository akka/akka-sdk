/* Wraps each table.dense in a scroller with a filter, sortable headers and a CSV
   download. See css/dense-tables.css. */
(function () {
  function cellText(cell) {
    return cell.textContent.replace(/\s+/g, ' ').trim();
  }

  function csvEscape(value) {
    return '"' + value.replace(/"/g, '""') + '"';
  }

  function enhance(table) {
    var body = table.tBodies[0];
    var headers = Array.prototype.slice.call(table.tHead.rows[0].cells);
    var wrapper = document.createElement('div');
    var scroll = document.createElement('div');
    var toolbar = document.createElement('div');
    var filter = document.createElement('input');
    var count = document.createElement('span');
    var download = document.createElement('a');
    var filename = document.title.split(' :: ')[0].trim().toLowerCase().replace(/[^a-z0-9]+/g, '-') + '.csv';

    // tables.css resets every column width to auto unless the table opts out; a
    // dense table takes its widths from the cols= proportions.
    table.classList.add('fixed-cols');

    wrapper.className = 'dense-breakout';
    scroll.className = 'dense-scroll';
    toolbar.className = 'dense-toolbar';
    filter.type = 'search';
    filter.placeholder = 'Filter all columns';
    filter.setAttribute('aria-label', 'Filter rows');
    download.href = '#';
    download.textContent = 'Download CSV';
    toolbar.appendChild(filter);
    toolbar.appendChild(count);
    toolbar.appendChild(download);
    table.parentNode.insertBefore(wrapper, table);
    wrapper.appendChild(toolbar);
    wrapper.appendChild(scroll);
    scroll.appendChild(table);

    Array.prototype.forEach.call(body.rows, function (row) {
      if (row.cells.length === 1) row.className += ' dense-group';
    });

    function dataRows() {
      return Array.prototype.filter.call(body.rows, function (r) { return !/dense-group/.test(r.className); });
    }

    function applyFilter() {
      var needle = filter.value.toLowerCase().trim();
      var shown = 0;
      var group = null;
      var groupHasRow = false;
      Array.prototype.forEach.call(body.rows, function (row) {
        if (/dense-group/.test(row.className)) {
          if (group) group.hidden = !groupHasRow;
          group = row;
          groupHasRow = false;
          return;
        }
        row.hidden = needle !== '' && row.textContent.toLowerCase().indexOf(needle) < 0;
        if (!row.hidden) { shown++; groupHasRow = true; }
      });
      if (group) group.hidden = !groupHasRow;
      count.textContent = shown + ' of ' + dataRows().length + ' rows';
    }

    filter.addEventListener('input', applyFilter);

    headers.forEach(function (th, index) {
      th.addEventListener('click', function () {
        var descending = th.getAttribute('aria-sort') === 'ascending';
        headers.forEach(function (h) { h.removeAttribute('aria-sort'); });
        th.setAttribute('aria-sort', descending ? 'descending' : 'ascending');
        // Group rows only make sense in source order, so sorting drops them.
        Array.prototype.slice.call(body.querySelectorAll('tr.dense-group')).forEach(function (g) { g.parentNode.removeChild(g); });
        dataRows().sort(function (a, b) {
          var order = cellText(a.cells[index]).localeCompare(cellText(b.cells[index]));
          return descending ? -order : order;
        }).forEach(function (r) { body.appendChild(r); });
        applyFilter();
      });
    });

    download.addEventListener('click', function (event) {
      event.preventDefault();
      var lines = [headers.map(function (h) { return csvEscape(cellText(h)); })];
      dataRows().forEach(function (r) {
        lines.push(Array.prototype.map.call(r.cells, function (c) { return csvEscape(cellText(c)); }));
      });
      var link = document.createElement('a');
      link.href = URL.createObjectURL(new Blob([lines.map(function (l) { return l.join(','); }).join('\n')], { type: 'text/csv' }));
      link.download = filename;
      link.click();
    });

    applyFilter();
  }

  document.addEventListener('DOMContentLoaded', function () {
    Array.prototype.forEach.call(document.querySelectorAll('table.dense'), enhance);
  });
})();
