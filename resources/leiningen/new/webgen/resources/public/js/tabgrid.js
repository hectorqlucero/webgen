/**
 * TabGrid - Client-side functionality
 * Handles parent selection, subgrid loading, and tab interactions
 */

window.TabGrid = (function() {
  'use strict';

  let selectedParentId = null;
  let currentEntity = null;

  /**
   * Initialize TabGrid on page load
   */
  function init() {
    const container = document.querySelector('.tabgrid-container');
    
    if (!container) return;

    currentEntity = container.dataset.entity;
    selectedParentId = container.dataset.selectedParentId;

    initTabListeners();
    initParentSelectorDataTable();
    initSelectParentButtons();
    initEditButtons();
    
    // Restore active tab from storage (after form submit redirect)
    let savedTab = sessionStorage.getItem('activeTab') || localStorage.getItem('activeTab');
    
    if (savedTab) {
      sessionStorage.removeItem('activeTab');
      localStorage.removeItem('activeTab');
      
      const tabLink = $('a[data-bs-target="' + savedTab + '"]');
      
      if (tabLink.length) {
        setTimeout(() => {
          tabLink[0].click();
        }, 100);
      }
    }
  }
  
  /**
   * Initialize edit button handlers
   */
  function initEditButtons() {
    // Edit button handler
    $(document).on('click', '.edit-btn', function(e) {
      e.preventDefault();
      const url = $(this).data('url');
      
      const activeTab = $('.nav-tabs .nav-link.active').data('bs-target');
      sessionStorage.setItem('activeTab', activeTab);
      localStorage.setItem('activeTab', activeTab);
      
      if (url) {
        $.ajax({
          url: url,
          success: function(html) {
            $('#exampleModal .modal-body').html(html);
            
            setTimeout(() => {
              const form = $('#exampleModal form');
              if (form.length) {
                if (form.find('input[name="return_tab"]').length === 0) {
                  form.append('<input type="hidden" name="return_tab" value="' + activeTab + '">');
                }
                
                form.off('submit').on('submit', function() {
                  sessionStorage.setItem('activeTab', activeTab);
                  localStorage.setItem('activeTab', activeTab);
                });
              }
            }, 100);
            
            const modalEl = document.getElementById('exampleModal');
            const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
            modal.show();
          },
          error: function() {
            console.error('[TabGrid] Failed to load edit form');
          }
        });
      }
    });
    
    // New button handler - for buttons with data-bs-toggle but href
    $(document).on('click', 'a[data-bs-toggle="modal"][href*="add-form"]', function(e) {
      e.preventDefault();
      const url = $(this).attr('href');
      
      if (url) {
        $.ajax({
          url: url,
          success: function(html) {
            $('#exampleModal .modal-body').html(html);
            $('#exampleModal').modal('show');
          },
          error: function(xhr, status, error) {
            console.error('[TabGrid] Failed to load add form:', error);
          }
        });
      }
    });
    
    // Subgrid "New" button handler
    $(document).on('click', '.add-subgrid-btn', function(e) {
      e.preventDefault();
      const subgridEntity = $(this).data('subgrid-entity');
      const parentId = $(this).data('parent-id');
      const url = '/admin/' + subgridEntity + '/add-form/' + parentId;
      
      const activeTab = $('.nav-tabs .nav-link.active').data('bs-target');
      sessionStorage.setItem('activeTab', activeTab);
      localStorage.setItem('activeTab', activeTab);
      
      $.ajax({
        url: url,
        success: function(html) {
          $('#exampleModal .modal-body').html(html);
          
          setTimeout(() => {
            const form = $('#exampleModal form');
            if (form.length) {
              if (form.find('input[name="return_tab"]').length === 0) {
                form.append('<input type="hidden" name="return_tab" value="' + activeTab + '">');
              }
              
              form.off('submit').on('submit', function() {
                sessionStorage.setItem('activeTab', activeTab);
                localStorage.setItem('activeTab', activeTab);
              });
            }
          }, 100);
          
          const modalEl = document.getElementById('exampleModal');
          const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
          modal.show();
        },
        error: function(xhr, status, error) {
          console.error('[TabGrid] Failed to load subgrid add form:', error);
        }
      });
    });
  }

  /**
   * Initialize parent selector modal DataTable
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
    $(document).on('click', '.nav-tabs .nav-link', function(e) {
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
    $(document).on('shown.bs.tab', '.nav-tabs .nav-link', function(event) {
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
    
    $(document).on('shown.bs.tab', 'a[data-bs-toggle="tab"]', function(event) {
      // Additional handler for any tab element
    });
  }

  /**
   * Initialize select parent button clicks
   */
  function initSelectParentButtons() {
    document.addEventListener('click', function(e) {
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
      success: function(response) {
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
      error: function(xhr, status, error) {
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
      render: function(data, type, row) {
        const editUrl = '/admin/' + subgridEntity + '/edit-form/' + row.id;
        const deleteUrl = '/admin/' + subgridEntity + '/delete/' + row.id;
        const actions = window.subgridActions && window.subgridActions[subgridEntity] 
          ? window.subgridActions[subgridEntity] 
          : {edit: true, delete: true};
        
        let buttons = '';
        if (actions.edit) {
          buttons += `
            <button class="btn btn-warning btn-sm edit-btn" data-url="${editUrl}">
              <i class="bi bi-pencil"></i> Edit
            </button>`;
        }
        if (actions.delete) {
          buttons += `
            <a href="${deleteUrl}" class="btn btn-danger btn-sm" onclick="return confirm('Are you sure?')">
              <i class="bi bi-trash"></i> Delete
            </a>`;
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
    setTimeout(function() {
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
        <div class="alert alert-${type}">
          <i class="bi bi-info-circle me-2"></i>
          ${message}
        </div>
      `;
    }
  }

  // Initialize on DOM ready
  $(document).ready(init);

  // Public API
  return {
    init: init,
    selectParent: selectParent,
    selectParentFromRow: selectParentFromRow
  };
})();
