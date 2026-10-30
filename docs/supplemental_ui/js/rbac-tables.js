/* Adds a search box to the tables with the class rbac-permissions or rbac-roles. The
   tables are generated at build time by bin/generate-rbac-tables.mjs. The search
   matches any part of the first column, ignoring case. */
(function () {
  function cellText(cell) {
    return cell.textContent.replace(/\s+/g, ' ').trim().toLowerCase();
  }

  // A group row has a single header cell that spans the table. Its span has the class
  // rbac-type or rbac-resource.
  function groupLevel(row) {
    if (row.cells.length !== 1 || row.cells[0].tagName !== 'TH') return null;
    return row.cells[0].querySelector('.rbac-type') ? 'type' : 'resource';
  }

  // Shows the rows that match, and the group rows that have a visible row below them.
  function applyFilter(rows, query) {
    var shown = 0;
    var visibleBelow = { type: false, resource: false };
    for (var i = rows.length - 1; i >= 0; i--) {
      var row = rows[i];
      if (row.level === null) {
        row.element.hidden = row.text.indexOf(query) === -1;
        if (!row.element.hidden) {
          shown++;
          visibleBelow.type = true;
          visibleBelow.resource = true;
        }
      } else {
        row.element.hidden = !visibleBelow[row.level];
        visibleBelow[row.level] = false;
        if (row.level === 'type') visibleBelow.resource = false;
      }
    }
    return shown;
  }

  function enhance(table, label) {
    var rows = Array.prototype.map.call(table.tBodies[0].rows, function (row) {
      var level = groupLevel(row);
      if (level) row.classList.add('rbac-group', 'rbac-group-' + level);
      return { element: row, level: level, text: level ? '' : cellText(row.cells[0]) };
    });

    var box = document.createElement('div');
    var input = document.createElement('input');
    var count = document.createElement('span');
    box.className = 'rbac-search';
    count.className = 'rbac-count';
    count.setAttribute('aria-live', 'polite');
    input.type = 'search';
    input.placeholder = 'Search by ' + label + ' name';
    input.setAttribute('aria-label', 'Search by ' + label + ' name');
    box.appendChild(input);
    box.appendChild(count);
    table.parentNode.insertBefore(box, table);

    input.addEventListener('input', function () {
      var query = input.value.trim().toLowerCase();
      var shown = applyFilter(rows, query);
      count.textContent = query ? shown + ' matching' : '';
    });
  }

  function init() {
    Array.prototype.forEach.call(document.querySelectorAll('table.rbac-permissions'), function (table) {
      enhance(table, 'permission');
    });
    Array.prototype.forEach.call(document.querySelectorAll('table.rbac-roles'), function (table) {
      enhance(table, 'role');
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
