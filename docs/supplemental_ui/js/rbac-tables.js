/* Fills the tables with the class rbac-permissions or rbac-roles from the RBAC
   documents that the Akka control plane serves. The first body row of each table
   is the template. The script replaces it with the rows it renders. */
(function () {
  var BASE_URL = 'https://api.kalix.dev/resources/rbac/';

  function el(tag, text, className) {
    var node = document.createElement(tag);
    if (text !== undefined && text !== null) node.textContent = text;
    if (className) node.className = className;
    return node;
  }

  function cell(content, className) {
    var td = el('td', null, 'tableblock halign-left valign-top');
    var p = el('p', null, 'tableblock');
    if (content) p.appendChild(content);
    td.appendChild(p);
    if (className) td.classList.add(className);
    return td;
  }

  function code(text) {
    return el('code', text);
  }

  function codeList(names, linkFor) {
    var holder = document.createDocumentFragment();
    names.forEach(function (name, i) {
      if (i > 0) holder.appendChild(document.createElement('br'));
      var c = code(name);
      if (linkFor) {
        var a = el('a');
        a.href = linkFor(name);
        a.appendChild(c);
        holder.appendChild(a);
      } else {
        holder.appendChild(c);
      }
    });
    return holder;
  }

  function text(value) {
    return document.createTextNode(value || '');
  }

  function roleAnchor(roleName) {
    return 'role-' + roleName.replace(/[^a-zA-Z0-9]+/g, '-');
  }

  function groupRow(columns, title, description, level) {
    var tr = el('tr', null, 'rbac-group rbac-group-' + level);
    tr.dataset.level = level;
    var th = el('th', null, 'tableblock halign-left valign-top');
    th.colSpan = columns;
    th.appendChild(code(title));
    if (description) th.appendChild(el('span', ' ' + description, 'rbac-group-description'));
    tr.appendChild(th);
    return tr;
  }

  function permissionRows(doc, columns) {
    var rows = [];
    doc.types.forEach(function (type) {
      rows.push(groupRow(columns, type.name, type.description, 'type'));
      function add(permission, prefix, grantedOn) {
        var name = prefix + '.' + permission.name;
        var tr = el('tr');
        tr.dataset.search = name.toLowerCase();
        tr.appendChild(cell(code(name)));
        tr.appendChild(cell(text(permission.description)));
        var granted = permission.grantedOn || grantedOn;
        tr.appendChild(cell(granted ? code(granted) : text('')));
        tr.appendChild(cell(codeList(permission.roles || [], function (role) {
          return 'roles.html#' + roleAnchor(role);
        })));
        rows.push(tr);
      }
      (type.permissions || []).forEach(function (p) {
        add(p, type.name, type.grantedOn);
      });
      (type.resources || []).forEach(function (resource) {
        var prefix = type.name + '.' + resource.name;
        rows.push(groupRow(columns, prefix, resource.description, 'resource'));
        resource.permissions.forEach(function (p) {
          add(p, prefix, resource.grantedOn || type.grantedOn);
        });
      });
    });
    return rows;
  }

  function roleRows(doc) {
    return doc.map(function (role) {
      var tr = el('tr');
      tr.id = roleAnchor(role.roleName);
      tr.dataset.search = role.roleName.toLowerCase();
      tr.appendChild(cell(code(role.roleName)));
      tr.appendChild(cell(text(role.description)));
      tr.appendChild(cell(codeList(role.permissions)));
      return tr;
    });
  }

  // Shows only the rows that match, and the group rows that still have a visible row below them.
  function applyFilter(body, query) {
    var q = query.trim().toLowerCase();
    var rows = Array.prototype.slice.call(body.rows);
    var shown = 0;
    rows.forEach(function (row) {
      if (row.dataset.search !== undefined) {
        var match = row.dataset.search.indexOf(q) !== -1;
        row.hidden = !match;
        if (match) shown++;
      }
    });
    var lastType = null;
    var lastResource = null;
    rows.forEach(function (row) {
      if (row.dataset.level === 'type') {
        lastType = row;
        lastResource = null;
        row.hidden = true;
      } else if (row.dataset.level === 'resource') {
        lastResource = row;
        row.hidden = true;
      } else if (!row.hidden) {
        if (lastResource) lastResource.hidden = false;
        if (lastType) lastType.hidden = false;
      }
    });
    return shown;
  }

  function addSearch(table, body, label) {
    var box = el('div', null, 'rbac-search');
    var input = el('input');
    input.type = 'search';
    input.placeholder = 'Search by ' + label + ' name';
    input.setAttribute('aria-label', 'Search by ' + label + ' name');
    var count = el('span', null, 'rbac-count');
    count.setAttribute('aria-live', 'polite');
    box.appendChild(input);
    box.appendChild(count);
    table.parentNode.insertBefore(box, table);
    input.addEventListener('input', function () {
      var shown = applyFilter(body, input.value);
      count.textContent = input.value.trim() ? shown + ' matching' : '';
    });
  }

  function fill(table, resource, render) {
    var body = table.tBodies[0];
    var columns = table.tHead.rows[0].cells.length;
    var template = body.rows[0];
    fetch(BASE_URL + resource)
      .then(function (response) {
        if (!response.ok) throw new Error('HTTP ' + response.status);
        return response.json();
      })
      .then(function (doc) {
        var rows = render(doc, columns);
        body.removeChild(template);
        rows.forEach(function (row) {
          body.appendChild(row);
        });
        addSearch(table, body, resource === 'roles.json' ? 'role' : 'permission');
      })
      .catch(function (error) {
        var td = template.cells[0];
        template.removeAttribute('data-search');
        while (template.cells.length > 1) template.removeChild(template.cells[1]);
        td.colSpan = columns;
        td.textContent = 'Could not load ' + BASE_URL + resource + ' (' + error.message + ').';
      });
  }

  function init() {
    Array.prototype.forEach.call(document.querySelectorAll('table.rbac-permissions'), function (table) {
      fill(table, 'permissions.json', permissionRows);
    });
    Array.prototype.forEach.call(document.querySelectorAll('table.rbac-roles'), function (table) {
      fill(table, 'roles.json', roleRows);
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
