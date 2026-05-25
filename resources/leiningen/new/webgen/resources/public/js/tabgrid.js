/**
 * TabGrid - Client-side functionality
 * Handles parent selection, subgrid loading, and tab interactions
 */

window.TabGrid = (function () {
  'use strict';

  let selectedParentId = null;
  let currentEntity = null;
  let editButtonsBound = false;

  /** Escape HTML special characters to prevent XSS */
  function escapeHtml(str) {
    var div = document.createElement('div');
    div.appendChild(document.createTextNode(str));
    return div.innerHTML;
  }

  /**
   * Get a human-readable entity title for the modal header.
   * Tries the page heading first, falls back to extracting from URL.
   */
  function getEntityTitle(url) {
    // Try the main page heading rendered by tabgrid
    var heading = document.querySelector('.tabgrid-container h3, .card-body h4');
    if (heading) {
      // Strip badge text (e.g. "12 Items") by taking only the first text node
      var text = heading.childNodes[0] ? heading.childNodes[0].textContent : '';
      // Heading may contain icon text; get the clean part
      if (!text || text.trim().length === 0) {
        for (var i = 0; i < heading.childNodes.length; i++) {
          var node = heading.childNodes[i];
          if (node.nodeType === 3 && node.textContent.trim().length > 0) {
            text = node.textContent;
            break;
          }
        }
      }
      if (text && text.trim().length > 0) return text.trim();
    }
    // Fallback: extract entity name from URL like /admin/propiedades/add-form
    if (url) {
      var match = url.match(/\/admin\/([^\/]+)\//);
      if (match) {
        var name = match[1].replace(/_/g, ' ');
        return name.charAt(0).toUpperCase() + name.slice(1);
      }
    }
    return '';
  }

  /**
   * Initialize TabGrid on page load
   */
  function init() {
    const container = document.querySelector('.tabgrid-container');

    if (!container) return;

    currentEntity = container.dataset.entity;
    selectedParentId = container.dataset.selectedParentId;

    initTabListeners();
    initListPanel();
    initEditButtons();
    focusSelectedRecordInList();

    // Restore open accordion sections after page reload (e.g. after M2M link)
    restoreAccordionState();
    // Open accordion specified in URL param (e.g. after subgrid save redirect)
    openAccordionFromUrl();
  }

  /**
   * Ensure selected parent is visible in the left record list after redirects.
   */
  function focusSelectedRecordInList() {
    if (!selectedParentId) return;

    var list = document.querySelector('.tg-record-list');
    if (!list) return;

    var activeItem = list.querySelector('.tg-record-item.active') ||
      list.querySelector('.tg-record-item[data-parent-id="' + String(selectedParentId) + '"]');

    if (!activeItem) return;

    if (!activeItem.classList.contains('active')) {
      activeItem.classList.add('active');
    }

    activeItem.scrollIntoView({ block: 'nearest', behavior: 'smooth' });

    activeItem.classList.add('tg-record-item-spotlight');
    setTimeout(function () {
      activeItem.classList.remove('tg-record-item-spotlight');
    }, 900);
  }

  /**
   * Initialize edit button handlers
   */
  function initEditButtons() {
    if (editButtonsBound) return;
    editButtonsBound = true;

    // Edit button handler
    $(document).on('click', '.edit-btn', function (e) {
      e.preventDefault();
      const url = $(this).data('url');

      if (url) {
        // Set modal title for edit
        var entityTitle = getEntityTitle();
        var editLabel = (window.i18nStrings && window.i18nStrings.edit) || 'Edit';
        $('#exampleModalLabel').text(entityTitle ? editLabel + ' ' + entityTitle : editLabel);

        $.ajax({
          url: url,
          success: function (html) {
            $('#exampleModal .modal-body').html(html);

            setTimeout(() => {
              const form = $('#exampleModal form');
              if (form.length) {
                form.off('submit').on('submit', function () {
                  saveAccordionState();
                });
              }
            }, 100);

            const modalEl = document.getElementById('exampleModal');
            const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
            modal.show();
          },
          error: function () {
            console.error('[TabGrid] Failed to load edit form');
          }
        });
      }
    });

    // New button handler - for buttons with data-bs-toggle but href
    $(document).on('click', 'a[data-bs-toggle="modal"][href*="add-form"]', function (e) {
      e.preventDefault();
      const url = $(this).attr('href');

      if (url) {
        // Set modal title for new record
        var entityTitle = getEntityTitle(url);
        var newLabel = (window.i18nStrings && window.i18nStrings.new) || 'New';
        $('#exampleModalLabel').text(entityTitle ? newLabel + ' ' + entityTitle : newLabel);

        $.ajax({
          url: url,
          success: function (html) {
            $('#exampleModal .modal-body').html(html);
            var modalEl = document.getElementById('exampleModal');
            var modal = bootstrap.Modal.getOrCreateInstance(modalEl);
            modal.show();
          },
          error: function (xhr, status, error) {
            console.error('[TabGrid] Failed to load add form:', error);
          }
        });
      }
    });

    // Subgrid "New" button handler
    $(document).on('click', '.add-subgrid-btn', function (e) {
      e.preventDefault();
      const subgridEntity = $(this).data('subgrid-entity');
      const parentId = $(this).data('parent-id');
      const parentEntity = $(this).data('parent-entity');
      // Capture the accordion pane now — it is already open when user clicks "New"
      const accordionPane = this.closest('.accordion-collapse');
      const url = '/admin/' + subgridEntity + '/add-form/' + parentId + '?parent_entity=' + encodeURIComponent(parentEntity);

      // Set modal title for new subgrid record
      var subgridTitle = $(this).closest('.tab-pane').find('.card-header h6, .fw-bold').first().text() || subgridEntity;
      var newLabel = (window.i18nStrings && window.i18nStrings.new) || 'New';
      $('#exampleModalLabel').text(newLabel + ' ' + subgridTitle.trim());

      $.ajax({
        url: url,
        success: function (html) {
          $('#exampleModal .modal-body').html(html);

          setTimeout(() => {
            const form = $('#exampleModal form')[0];
            if (form) {
              $(form).off('submit').on('submit', function (evt) {
                evt.preventDefault();
                // Save accordion state before page reload so restoreAccordionState()
                // can reopen it after the server redirects back to the parent entity.
                if (accordionPane) {
                  saveAccordionState(accordionPane);
                }
                // Submit form normally; server saves and redirects to parent page.
                form.submit();
              });
            }
          }, 100);

          const modalEl = document.getElementById('exampleModal');
          const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
          modal.show();
        },
        error: function (xhr, status, error) {
          console.error('[TabGrid] Failed to load subgrid add form:', error);
        }
      });
    });
  }

  /**
   * Save open accordion section IDs to sessionStorage before a page reload.
   * Pass the element that triggered the action so we can walk up to its
   * ancestor .accordion-collapse as a guaranteed reference (querySelectorAll
   * may miss it while a Bootstrap modal is actively open on top).
   */
  function saveAccordionState(referenceEl) {
    var ids = [];
    // Capture the current open set of accordion sections. This allows us to
    // restore the exact visible state after reload instead of defaulting back
    // to the parent contact accordion.
    document.querySelectorAll('.accordion-collapse.show').forEach(function (el) {
      if (el.id && ids.indexOf(el.id) === -1) ids.push(el.id);
    });

    // Ensure the pane that triggered the action is included, even if Bootstrap
    // has not yet updated the DOM state at the time of the click.
    if (referenceEl && typeof referenceEl.closest === 'function') {
      var directPane = referenceEl.closest('.accordion-collapse');
      if (directPane && directPane.id && ids.indexOf(directPane.id) === -1) {
        ids.push(directPane.id);
      }
    }

    if (ids.length > 0) {
      sessionStorage.setItem('openAccordions', JSON.stringify(ids));
    } else {
      sessionStorage.removeItem('openAccordions');
    }
  }

  /**
   * Open an accordion pane specified in the URL's open_accordion query parameter.
   * Used after subgrid save redirects to reopen the correct subgrid section.
   */
  function openAccordionFromUrl() {
    var params = new URLSearchParams(window.location.search);
    var accordionId = params.get('open_accordion');
    if (!accordionId) return;
    setTimeout(function () {
      var pane = document.getElementById(accordionId);
      if (pane && !pane.classList.contains('show')) {
        bootstrap.Collapse.getOrCreateInstance(pane, { toggle: false }).show();
      }
      // Remove the open_accordion parameter after using it so a later reload
      // does not reopen the same accordion unexpectedly.
      params.delete('open_accordion');
      var newUrl = window.location.origin + window.location.pathname;
      var query = params.toString();
      if (query) newUrl += '?' + query;
      newUrl += window.location.hash;
      window.history.replaceState({}, '', newUrl);
    }, 50);
  }

  function restoreAccordionState() {
    var saved = sessionStorage.getItem('openAccordions');
    if (!saved) return;
    sessionStorage.removeItem('openAccordions');
    try {
      var ids = JSON.parse(saved);
      if (!Array.isArray(ids) || ids.length === 0) return;

      // Defer to next paint cycle so Bootstrap has finished its own DOM setup.
      setTimeout(function () {
        // Collapse everything first, then reopen only the saved panes.
        document.querySelectorAll('.accordion-collapse').forEach(function (el) {
          if (el.id && ids.indexOf(el.id) === -1) {
            bootstrap.Collapse.getOrCreateInstance(el, { toggle: false }).hide();
          }
        });

        ids.forEach(function (id) {
          var pane = document.getElementById(id);
          if (pane && !pane.classList.contains('show')) {
            bootstrap.Collapse.getOrCreateInstance(pane, { toggle: false }).show();
          }
        });
      }, 50);
    } catch (e) { }
  }

  /**
   * Initialize left-panel record list clicks.
   * Clicking any .tg-record-item selects that parent via page reload.
   */
  function initListPanel() {
    document.addEventListener('click', function (e) {
      const item = e.target.closest('.tg-record-item');
      if (item && item.dataset.parentId) {
        selectParent(item.dataset.parentId);
      }
    });
  }

  /**
   * Filter the left-panel record list by label text.
   * Called via oninput on the search input.
   */
  function filterRecordList(input) {
    var query = input.value.toLowerCase();
    document.querySelectorAll('.tg-record-item').forEach(function (item) {
      var label = item.querySelector('.tg-record-label');
      var text = label ? label.textContent.toLowerCase() : item.textContent.toLowerCase();
      item.style.display = text.includes(query) ? '' : 'none';
    });
  }

  /**
   * Initialize parent selector modal DataTable (legacy — kept for compatibility)
   */
  function initParentSelectorDataTable() {
    const tableId = currentEntity + '-select-table';
    const table = $('#' + tableId);

    if (table.length && !$.fn.DataTable.isDataTable(table)) {
      table.DataTable({
        responsive: true,
        pageLength: 10,
        language: window.i18nStrings || {},
        order: [[1, 'asc']]
      });
    }
  }

  /**
   * Initialize tab change listeners
   */
  function initTabListeners() {
    const tabs = document.querySelectorAll('.nav-tabs .nav-link');

    // Manually initialize Bootstrap tabs
    tabs.forEach((tabEl) => {
      if (typeof bootstrap !== 'undefined' && bootstrap.Tab) {
        new bootstrap.Tab(tabEl);
      }
    });

    // Use event delegation on document to catch all tab clicks
    $(document).on('click', '.nav-tabs .nav-link', function (e) {
      e.preventDefault();
      const tab = this;
      const targetId = tab.dataset.bsTarget;

      // Manually handle tab switching
      const allTabs = document.querySelectorAll('.nav-tabs .nav-link');
      const allPanes = document.querySelectorAll('.tab-pane');

      // Remove active from all tabs and panes
      allTabs.forEach(t => t.classList.remove('active'));
      allPanes.forEach(p => p.classList.remove('show', 'active'));

      // Add active to clicked tab
      tab.classList.add('active');

      // Show target pane
      const targetPane = document.querySelector(targetId);
      if (targetPane) {
        targetPane.classList.add('show', 'active');

        // If it's a subgrid tab, load data
        if (targetPane.dataset.subgridEntity) {
          // Check if already loaded
          const tableWrapper = targetPane.querySelector('.subgrid-table-wrapper');
          const dataTableWrapper = tableWrapper ? tableWrapper.querySelector('.dataTables_wrapper') : null;
          const isLoaded = targetPane.dataset.loaded === 'true';

          if (!isLoaded) {
            setTimeout(() => {
              loadSubgridData(targetPane);
            }, 50);
          }
        }
      }
    });

    // Bootstrap tab event listeners
    $(document).on('shown.bs.tab', '.nav-tabs .nav-link', function (event) {
      const targetId = event.target.dataset.bsTarget;
      const targetPane = document.querySelector(targetId);

      if (targetPane && targetPane.dataset.subgridEntity) {
        const tableWrapper = targetPane.querySelector('.subgrid-table-wrapper');
        const hasData = tableWrapper && tableWrapper.querySelector('.dataTable');

        if (!hasData) {
          loadSubgridData(targetPane);
        }
      }
    });

    $(document).on('shown.bs.tab', 'a[data-bs-toggle="tab"]', function (event) {
      // Additional handler for any tab element
    });

    // Accordion expand → lazy-load 1:many subgrids
    $(document).on('shown.bs.collapse', '.accordion-collapse', function (e) {
      var pane = e.target;
      if (pane.dataset.subgridEntity && pane.dataset.loaded !== 'true') {
        loadSubgridData(pane);
      }
    });
  }

  /**
   * Initialize select parent button clicks
   */
  function initSelectParentButtons() {
    document.addEventListener('click', function (e) {
      if (e.target.closest('.select-parent-btn')) {
        e.preventDefault();
        const btn = e.target.closest('.select-parent-btn');
        const parentId = btn.dataset.parentId;
        selectParent(parentId);
      }
    });
  }

  /**
   * Select a parent record (reload page with new parent)
   */
  function selectParent(elementOrId) {
    let parentId;

    if (typeof elementOrId === 'object' && elementOrId.nodeType) {
      const row = elementOrId.closest('tr.parent-row');
      parentId = row ? row.dataset.parentId : null;
    } else {
      parentId = elementOrId;
    }

    if (!parentId) {
      console.error('[TabGrid] No parent ID found');
      return;
    }

    // Reload page with new parent ID
    const url = new URL(window.location.href);
    url.searchParams.set('id', parentId);
    window.location.href = url.toString();
  }

  /**
   * Select parent from row click
   */
  function selectParentFromRow(row) {
    const parentId = row.dataset.parentId;
    if (parentId) {
      selectParent(parentId);
    }
  }

  /**
   * Load subgrid data via AJAX
   */
  function loadSubgridData(pane) {
    const subgridEntity = pane.dataset.subgridEntity;
    const foreignKey = pane.dataset.foreignKey;

    if (!selectedParentId) {
      showSubgridMessage(pane, 'Please select a parent record first', 'warning');
      return;
    }

    const loadingDiv = pane.querySelector('.subgrid-loading');
    const tableWrapper = pane.querySelector('.subgrid-table-wrapper');

    if (loadingDiv) loadingDiv.style.display = 'block';
    if (tableWrapper) tableWrapper.style.display = 'none';

    // AJAX request to load subgrid data
    $.ajax({
      url: '/tabgrid/load-subgrid',
      method: 'GET',
      data: {
        entity: currentEntity,
        subgrid_entity: subgridEntity,
        parent_id: selectedParentId,
        foreign_key: foreignKey
      },
      success: function (response) {
        if (response.success) {
          // Store actions configuration globally for this subgrid
          if (!window.subgridActions) window.subgridActions = {};
          window.subgridActions[subgridEntity] = response.actions;

          renderSubgridTable(pane, response.records, response.fields);
          pane.dataset.loaded = 'true';
        } else {
          if (loadingDiv) loadingDiv.style.display = 'none';
          showSubgridMessage(pane, 'Error: ' + response.error, 'danger');
        }
      },
      error: function (xhr, status, error) {
        console.error('[TabGrid] AJAX error:', error);
        if (loadingDiv) loadingDiv.style.display = 'none';
        showSubgridMessage(pane, 'Failed to load subgrid data', 'danger');
      }
    });
  }

  /**
   * Render subgrid data into DataTable
   */
  function renderSubgridTable(pane, records, fields) {
    const subgridEntity = pane.dataset.subgridEntity;
    const tableId = currentEntity + '-' + subgridEntity.replace(/[^a-z0-9]/gi, '-') + '-table';
    const table = $('#' + tableId);
    const tableWrapper = pane.querySelector('.subgrid-table-wrapper');
    const loadingDiv = pane.querySelector('.subgrid-loading');

    // Hide loading spinner
    if (loadingDiv) {
      loadingDiv.style.display = 'none';
    }

    // Destroy existing DataTable BEFORE showing wrapper
    if ($.fn.DataTable.isDataTable(table)) {
      table.DataTable().destroy();
    }

    // NOW show the table wrapper with explicit styles
    if (tableWrapper) {
      tableWrapper.style.display = 'block';
      tableWrapper.style.visibility = 'visible';
      tableWrapper.style.opacity = '1';
      tableWrapper.style.minHeight = '200px';
    }

    // Build columns from fields
    const columns = [];
    for (const [fieldId, fieldLabel] of Object.entries(fields)) {
      columns.push({ data: fieldId, title: fieldLabel });
    }

    // Add actions column
    columns.push({
      data: null,
      title: 'Actions',
      render: function (data, type, row) {
        var safeId = escapeHtml(String(row.id));
        var safeEntity = escapeHtml(String(subgridEntity));
        const editUrl = '/admin/' + safeEntity + '/edit-form/' + safeId;
        const deleteUrl = '/admin/' + safeEntity + '/delete/' + safeId;
        const actions = window.subgridActions && window.subgridActions[subgridEntity]
          ? window.subgridActions[subgridEntity]
          : { edit: true, delete: true };

        let buttons = '';
        if (actions.edit) {
          buttons += `
            <button class="btn btn-warning btn-sm edit-btn" data-url="${editUrl}">
              <i class="bi bi-pencil"></i> Edit
            </button>`;
        }
        if (actions.delete) {
          buttons += `
            <button type="button" class="btn btn-danger btn-sm delete-btn" data-delete-url="${deleteUrl}">
              <i class="bi bi-trash"></i> Delete
            </button>`;
        }
        return `<div class="btn-group btn-group-sm">${buttons}</div>`;
      }
    });

    const dt = table.DataTable({
      data: records,
      columns: columns,
      responsive: true,
      pageLength: 5,
      language: window.i18nStrings || {}
    });

    // Force DataTables to recalculate column widths
    setTimeout(function () {
      dt.columns.adjust().draw();
    }, 100);
  }

  /**
   * Show message in subgrid pane
   */
  function showSubgridMessage(pane, message, type) {
    const loadingDiv = pane.querySelector('.subgrid-loading');
    if (loadingDiv) {
      loadingDiv.innerHTML = `
        <div class="alert alert-${escapeHtml(type)}">
          <i class="bi bi-info-circle me-2"></i>
          ${escapeHtml(message)}
        </div>
      `;
    }
  }

  // --- Intercept DELETE buttons and handle via POST AJAX ---
  document.addEventListener('click', function (e) {
    const btn = e.target.closest && e.target.closest('button.delete-btn[data-delete-url], a.btn-danger[data-delete-url]');
    if (!btn) return;
    e.preventDefault();
    e.stopImmediatePropagation();

    var confirmMsg = (window.i18nStrings && window.i18nStrings.confirmDelete) || 'Are you sure?';
    if (!window.confirm(confirmMsg)) return;

    // Save accordion state before reload so it can be restored
    saveAccordionState(btn);

    var tokenEl = document.querySelector('input[name="__anti-forgery-token"]');
    var headers = { 'X-Requested-With': 'XMLHttpRequest' };
    var body = '';
    if (tokenEl) {
      headers['Content-Type'] = 'application/x-www-form-urlencoded';
      body = '__anti-forgery-token=' + encodeURIComponent(tokenEl.value);
    }

    fetch(btn.getAttribute('data-delete-url'), {
      method: 'POST',
      credentials: 'same-origin',
      headers: headers,
      body: body
    })
      .then(resp => {
        if (resp.ok) {
          window.location.reload();
        } else if (resp.status === 403) {
          alert((window.i18nStrings && window.i18nStrings.errorNotAuthorized) || 'Not authorized');
        } else {
          alert((window.i18nStrings && window.i18nStrings.errorServer) || 'Server error');
        }
      })
      .catch(() => alert((window.i18nStrings && window.i18nStrings.errorNetwork) || 'Network error'));
  }, true); // use capture phase

  // Initialize on DOM ready
  $(document).ready(init);

  // --- M2M: Inline confirm for Unlink (#6) ---
  document.addEventListener('click', function (e) {
    // Show inline confirm when Unlink button clicked
    const unlinkBtn = e.target.closest('.m2m-unlink-btn');
    if (unlinkBtn) {
      e.preventDefault();
      const form = unlinkBtn.closest('.m2m-dissociate-form');
      const confirm = form && form.querySelector('.m2m-unlink-confirm');
      if (confirm) {
        confirm.style.display = 'inline-flex';
        confirm.style.alignItems = 'center';
        confirm.style.gap = '4px';
        unlinkBtn.style.display = 'none';
      }
    }
    // Cancel inline confirm
    const cancelBtn = e.target.closest('.m2m-confirm-no');
    if (cancelBtn) {
      e.preventDefault();
      const form = cancelBtn.closest('.m2m-dissociate-form');
      const confirm = form && form.querySelector('.m2m-unlink-confirm');
      const unlinkBtnOrig = form && form.querySelector('.m2m-unlink-btn');
      if (confirm) confirm.style.display = 'none';
      if (unlinkBtnOrig) unlinkBtnOrig.style.display = '';
    }
  });

  // --- M2M: AJAX associate / dissociate (#5) ---
  function getAntiForgeryToken() {
    const el = document.querySelector('input[name="__anti-forgery-token"]');
    return el ? el.value : '';
  }

  function m2mPost(form, onSuccess) {
    const data = new URLSearchParams(new FormData(form));
    const token = getAntiForgeryToken();
    if (token) data.set('__anti-forgery-token', token);

    fetch(form.action, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: data.toString()
    }).then(function (resp) {
      if (resp.ok || resp.redirected) {
        onSuccess();
      } else {
        alert((window.i18nStrings && window.i18nStrings.errorServer) || 'Server error');
      }
    }).catch(function () {
      alert((window.i18nStrings && window.i18nStrings.errorNetwork) || 'Network error');
    });
  }

  /**
   * Fetch a fresh M2M pane fragment from the server and swap it in-place.
   * pane: the .m2m-pane element (must have data-entity, data-parent-id,
   *        data-subgrid-entity attributes).
   * Falls back to a full page reload on network error.
   */
  function refreshM2MPane(pane) {
    if (!pane || !pane.dataset) { window.location.reload(); return; }
    var url = '/tabgrid/m2m-pane'
      + '?entity=' + encodeURIComponent(pane.dataset.entity)
      + '&parent_id=' + encodeURIComponent(pane.dataset.parentId)
      + '&subgrid_entity=' + encodeURIComponent(pane.dataset.subgridEntity);
    fetch(url, { credentials: 'same-origin' })
      .then(function (resp) { return resp.text(); })
      .then(function (htmlText) {
        var tmp = document.createElement('div');
        tmp.innerHTML = htmlText.trim();
        var newPane = tmp.firstElementChild;
        if (newPane) pane.replaceWith(newPane);
      })
      .catch(function () { window.location.reload(); });
  }

  // Intercept dissociate form submission (confirm "yes" button submits the form)
  document.addEventListener('submit', function (e) {
    const form = e.target.closest('.m2m-dissociate-form');
    if (!form) return;
    e.preventDefault();
    const row = form.closest('tr');
    // Capture pane reference now, before the row might be removed.
    const pane = form.closest('.m2m-pane');
    m2mPost(form, function () {
      if (row) {
        row.style.transition = 'opacity 0.25s';
        row.style.opacity = '0';
        setTimeout(function () {
          row.remove();
          refreshM2MPane(pane);
        }, 250);
      } else {
        refreshM2MPane(pane);
      }
    });
  });

  // Intercept associate form submission
  document.addEventListener('submit', function (e) {
    const form = e.target.closest('.m2m-associate-form');
    if (!form) return;
    e.preventDefault();
    // Capture pane reference before modal might be closed.
    const pane = form.closest('.m2m-pane');
    const modal = form.closest('.modal');
    m2mPost(form, function () {
      if (modal) {
        const bsModal = bootstrap.Modal.getInstance(modal);
        if (bsModal) {
          // Wait for Bootstrap to finish hiding the modal, then refresh.
          modal.addEventListener('hidden.bs.modal', function () {
            refreshM2MPane(pane);
          }, { once: true });
          bsModal.hide();
          return;
        }
      }
      refreshM2MPane(pane);
    });
  });

  // Public API
  return {
    init: init,
    selectParent: selectParent,
    selectParentFromRow: selectParentFromRow,
    filterRecordList: filterRecordList,
    // #3: Filter M2M modal rows by search input
    filterM2MModal: function (input) {
      const query = input.value.toLowerCase();
      const table = input.closest('.modal-body').querySelector('.m2m-available-table');
      if (!table) return;
      Array.from(table.querySelectorAll('tbody tr')).forEach(function (row) {
        const text = row.textContent.toLowerCase();
        row.style.display = text.includes(query) ? '' : 'none';
      });
    }
  };
})();
