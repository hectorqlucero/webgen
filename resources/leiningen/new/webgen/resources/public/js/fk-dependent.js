// FK Dependent Selects and Create Modal JavaScript

(function () {
  'use strict';

  // debug switch - flip to true while troubleshooting
  var FK_DEBUG = false;
  function fkLog() {
    if (FK_DEBUG) console.log.apply(console, arguments);
  }

  // Initialize on DOM ready
  document.addEventListener('DOMContentLoaded', function () {
    fkLog('[FK] DOM loaded, initializing...');
    initDependentSelects();
  });

  // Initialize dependent select change handlers
  function initDependentSelects() {
    fkLog('[FK] initDependentSelects called');
    var dependentSelects = document.querySelectorAll('select[data-fk-parent]');
    fkLog('[FK] Found dependent selects:', dependentSelects.length);

    dependentSelects.forEach(function (select) {
      var parentField = select.getAttribute('data-fk-parent');
      var fkEntity = select.getAttribute('data-fk-entity');
      fkLog('[FK] Setting up:', { childId: select.id, parentField: parentField, fkEntity: fkEntity });

      var parentSelect = document.querySelector('[name="' + parentField + '"]');
      fkLog('[FK] Parent select found:', parentSelect ? parentSelect.id : 'NOT FOUND');

      if (parentSelect) {
        parentSelect.addEventListener('change', function () {
          fkLog('[FK] Change event fired on parent:', parentSelect.value);
          handleParentChange(select, parentSelect.value);
        });

        if (parentSelect.value) {
          fkLog('[FK] Parent already has value, loading options');
          handleParentChange(select, parentSelect.value);
        }
      } else {
        console.warn('[FK] WARNING: Parent select not found for field:', parentField);
      }
    });
  }

  // Handle parent field change - reload dependent options
  function handleParentChange(childSelect, parentValue) {
    var entity = childSelect.getAttribute('data-fk-entity');
    var parentField = childSelect.getAttribute('data-fk-parent');

    fkLog('[FK] handleParentChange called:', { entity: entity, parentField: parentField, parentValue: parentValue, childId: childSelect.id });

    if (!parentValue) {
      childSelect.innerHTML = '<option value="">-- Seleccionar --</option>';
      childSelect.disabled = true;
      return;
    }

    childSelect.disabled = true;
    childSelect.innerHTML = '<option value="">Cargando...</option>';

    var url = '/api/fk-options?entity=' + encodeURIComponent(entity) +
      '&parent-field=' + encodeURIComponent(parentField) +
      '&parent-value=' + encodeURIComponent(parentValue);

    fkLog('[FK] Fetching URL:', url);

    fetch(url)
      .then(function (response) {
        fkLog('[FK] Response status:', response.status);
        return response.json();
      })
      .then(function (data) {
        fkLog('[FK] Response data:', data);
        if (data.ok && data.options) {
          childSelect.innerHTML = data.options.map(function (opt) {
            var selected = opt.value === childSelect.getAttribute('data-fk-current-value') ? ' selected' : '';
            return '<option value="' + opt.value + '"' + selected + '>' + opt.label + '</option>';
          }).join('');
        } else {
          childSelect.innerHTML = '<option value="">-- Error --</option>';
          console.error('FK options error:', data.error);
        }
        childSelect.disabled = false;
      })
      .catch(function (error) {
        console.error('[FK] Fetch error:', error);
        childSelect.innerHTML = '<option value="">-- Error --</option>';
        childSelect.disabled = false;
      });
  }

  // Create modal HTML
  function createFkModalHtml(entity, fieldId, parentField, parentValue, fkFormFields) {
    var fields = fkFormFields ? fkFormFields.split(',') : ['nombre'];

    return '<div class="modal fade" id="fkCreateModal" tabindex="-1">' +
      '<div class="modal-dialog modal-lg">' +
      '<div class="modal-content">' +
      '<div class="modal-header bg-primary text-white">' +
      '<h5 class="modal-title">Agregar Nuevo</h5>' +
      '<button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>' +
      '</div>' +
      '<div class="modal-body">' +
      '<div id="fkCreateError" class="alert alert-danger d-none"></div>' +
      '<form id="fkCreateForm">' +
      '<input type="hidden" name="entity" value="' + entity + '">' +
      (parentField ? '<input type="hidden" name="' + parentField + '" value="' + parentValue + '">' : '') +
      '</form>' +
      '</div>' +
      '<div class="modal-footer">' +
      '<button type="button" class="btn btn-secondary btn-lg" data-bs-dismiss="modal">Cancelar</button>' +
      '<button type="button" class="btn btn-primary btn-lg" id="fkSaveBtn">Guardar</button>' +
      '</div>' +
      '</div>' +
      '</div>' +
      '</div>';
  }

  // Show modal
  window.showFkCreateModal = function (entity, fieldId, parentField, btn) {
    // derive actual entity from select's data attribute if available
    var selectEl = document.getElementById(fieldId);
    if (selectEl && selectEl.dataset.fkEntity) {
      entity = selectEl.dataset.fkEntity;
    }
    fkLog('[FK] showFkCreateModal called:', { entity: entity, fieldId: fieldId, parentField: parentField });

    var parentValue = '';
    if (parentField) {
      var parentSelect = document.querySelector('[name="' + parentField + '"]');
      if (parentSelect) {
        parentValue = parentSelect.value;
      }
    }

    var fkFormFields = selectEl ? selectEl.getAttribute('data-fk-form-fields') : '';

    // Get entity configuration
    fetch('/api/fk-modal-config?entity=' + encodeURIComponent(entity))
      .then(function (response) { return response.json(); })
      .then(function (config) {
        if (config.ok) {
          var modalHtml;
          if (config['form-html']) {
            modalHtml = createFkModalHtmlWithServerContent(entity, fieldId, parentField, parentValue, config['form-html']);
          } else if (config['form-fields']) {
            modalHtml = createFkModalHtmlWithConfig(entity, fieldId, parentField, parentValue, config['form-fields']);
          }
          if (modalHtml) {
            var modalContainer = document.createElement('div');
            modalContainer.innerHTML = modalHtml;
            // append the modal element directly (first child)
            var inserted = modalContainer.firstElementChild;
            document.body.appendChild(inserted);

            var modalEl = document.getElementById('fkCreateModal');
            var bsModal = new bootstrap.Modal(modalEl);
            bsModal.show();

            // Attach event listener to save button
            document.getElementById('fkSaveBtn').addEventListener('click', function () {
              submitFkCreateWithConfig(fieldId, config);
            });

            modalEl.addEventListener('hidden.bs.modal', function () {
              // remove the modal element itself, not its parent
              document.body.removeChild(modalEl);
            });
            return;
          }
        }
        console.error('Failed to load entity config:', config.error);
        createSimpleModal(entity, fieldId, parentField, parentValue);
      })
      .catch(function (error) {
        console.error('Error loading entity config:', error);
        createSimpleModal(entity, fieldId, parentField, parentValue);
      });
  };

  // Create simple modal as fallback
  function createSimpleModal(entity, fieldId, parentField, parentValue) {
    var selectEl = document.getElementById(fieldId);
    var fkFormFields = selectEl ? selectEl.getAttribute('data-fk-form-fields') : '';
    var modalHtml = createFkModalHtml(entity, fieldId, parentField, parentValue, fkFormFields);

    var modalContainer = document.createElement('div');
    modalContainer.innerHTML = modalHtml;
    document.body.appendChild(modalContainer.firstElementChild);

    var modalEl = document.getElementById('fkCreateModal');
    var bsModal = new bootstrap.Modal(modalEl);
    bsModal.show();

    // Attach event listener to save button
    document.getElementById('fkSaveBtn').addEventListener('click', function () {
      submitFkCreate(fieldId);
    });

    modalEl.addEventListener('hidden.bs.modal', function () {
      document.body.removeChild(document.getElementById('fkCreateModal').parentElement);
    });
  }

  // Create modal HTML from server-provided form markup
  function createFkModalHtmlWithServerContent(entity, fieldId, parentField, parentValue, contentHtml) {
    var hiddenParentHtml = parentField ? '<input type="hidden" name="' + parentField + '" value="' + parentValue + '">' : '';
    return '<div class="modal fade" id="fkCreateModal" tabindex="-1">' +
      '<div class="modal-dialog modal-lg">' +
      '<div class="modal-content">' +
      '<div class="modal-header bg-primary text-white">' +
      '<h5 class="modal-title">Agregar Nuevo</h5>' +
      '<button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>' +
      '</div>' +
      '<div class="modal-body">' +
      '<div id="fkCreateError" class="alert alert-danger d-none"></div>' +
      '<form id="fkCreateForm">' +
      '<input type="hidden" name="entity" value="' + entity + '">' +
      hiddenParentHtml +
      contentHtml +
      '</form>' +
      '</div>' +
      '<div class="modal-footer">' +
      '<button type="button" class="btn btn-secondary btn-lg" data-bs-dismiss="modal">Cancelar</button>' +
      '<button type="button" class="btn btn-primary btn-lg" id="fkSaveBtn">Guardar</button>' +
      '</div>' +
      '</div>' +
      '</div>' +
      '</div>';
  }

  // Create modal HTML with entity configuration
  function createFkModalHtmlWithConfig(entity, fieldId, parentField, parentValue, fieldsConfig) {
    var formFieldsHtml = fieldsConfig.map(function (field) {
      var label = field.label || field.id.charAt(0).toUpperCase() + field.id.slice(1).replace(/_/g, ' ');
      var type = field.type || 'text';
      var placeholder = field.placeholder || (label + '...');
      var required = field.required ? 'required' : '';

      var inputHtml = '<input type="' + type + '" class="form-control form-control-lg" name="' + field.id + '" placeholder="' + placeholder + '" ' + required + '>';

      return '<div class="mb-3">' +
        '<label class="form-label fw-semibold">' + label + '</label>' +
        inputHtml +
        '</div>';
    }).join('');

    var hiddenParentHtml = parentField ? '<input type="hidden" name="' + parentField + '" value="' + parentValue + '">' : '';

    return '<div class="modal fade" id="fkCreateModal" tabindex="-1">' +
      '<div class="modal-dialog modal-lg">' +
      '<div class="modal-content">' +
      '<div class="modal-header bg-primary text-white">' +
      '<h5 class="modal-title">Agregar Nuevo</h5>' +
      '<button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>' +
      '</div>' +
      '<div class="modal-body">' +
      '<div id="fkCreateError" class="alert alert-danger d-none"></div>' +
      '<form id="fkCreateForm">' +
      '<input type="hidden" name="entity" value="' + entity + '">' +
      hiddenParentHtml +
      formFieldsHtml +
      '</form>' +
      '</div>' +
      '<div class="modal-footer">' +
      '<button type="button" class="btn btn-secondary btn-lg" data-bs-dismiss="modal">Cancelar</button>' +
      '<button type="button" class="btn btn-primary btn-lg" id="fkSaveBtn">Guardar</button>' +
      '</div>' +
      '</div>' +
      '</div>' +
      '</div>';
  }

  // Submit the form with entity configuration
  window.submitFkCreateWithConfig = function (fieldId, config) {
    var form = document.getElementById('fkCreateForm');
    var errorDiv = document.getElementById('fkCreateError');

    errorDiv.classList.add('d-none');
    errorDiv.textContent = '';

    var formData = {};
    var formElements = form.elements;
    for (var i = 0; i < formElements.length; i++) {
      var el = formElements[i];
      if (el.name && el.value) {
        formData[el.name] = el.value;
      }
    }
    // include anti-forgery token if present on page
    var tokenEl = document.querySelector('input[name="anti-forgery-token"], input[name="__anti-forgery-token"]');
    if (tokenEl && tokenEl.name && tokenEl.value) {
      formData[tokenEl.name] = tokenEl.value;
    }

    // Validate required fields
    var hasErrors = false;
    (config['form-fields'] || []).forEach(function (field) {
      if (field.required && (!formData[field.id] || formData[field.id].trim() === '')) {
        errorDiv.textContent = (field.label || field.id) + ' es requerido';
        errorDiv.classList.remove('d-none');
        hasErrors = true;
      }
    });

    if (hasErrors) return;

    var xhr = new XMLHttpRequest();
    xhr.open('POST', '/api/fk-create');
    xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');

    xhr.onload = function () {
      try {
        var response = JSON.parse(xhr.responseText);

        if (response.ok) {
          var selectEl = document.getElementById(fieldId);
          if (selectEl) {
            if (response.new_id) {
              var newOption = document.createElement('option');
              newOption.value = response.new_id;
              newOption.textContent = response.new_label;
              newOption.selected = true;
              selectEl.appendChild(newOption);
            } else {
              // if server didn't supply an id (map-based save), simply reload
              // the options from the parent value so the new record appears.
              var pfield = selectEl.getAttribute('data-fk-parent');
              var psel = document.querySelector('[name="' + pfield + '"]');
              if (psel) {
                // hide modal first then refresh
                var modalEl2 = document.getElementById('fkCreateModal');
                var bsModal2 = bootstrap.Modal.getInstance(modalEl2);
                bsModal2.hide();
                setTimeout(function () {
                  handleParentChange(selectEl, psel.value);
                }, 0);
                return; // done
              }
            }
          }

          var modalEl = document.getElementById('fkCreateModal');
          var bsModal = bootstrap.Modal.getInstance(modalEl);
          bsModal.hide();

        } else {
          if (response.errors) {
            var errorMessages = Object.values(response.errors).join(', ');
            errorDiv.textContent = errorMessages;
          } else if (response.error) {
            errorDiv.textContent = response.error;
          } else {
            errorDiv.textContent = 'Error al guardar';
          }
          errorDiv.classList.remove('d-none');
        }
      } catch (e) {
        errorDiv.textContent = 'Error al procesar respuesta';
        errorDiv.classList.remove('d-none');
      }
    };

    xhr.onerror = function () {
      errorDiv.textContent = 'Error de conexión';
      errorDiv.classList.remove('d-none');
    };

    var params = 'entity=' + encodeURIComponent(formData.entity) +
      '&data=' + encodeURIComponent(JSON.stringify(formData));

    // attach CSRF token as top-level form parameter if we found one
    if (tokenEl && tokenEl.name && tokenEl.value) {
      params += '&' + encodeURIComponent(tokenEl.name) + '=' + encodeURIComponent(tokenEl.value);
      // also set header for good measure (ring accepts X-CSRF-Token)
      xhr.setRequestHeader('X-CSRF-Token', tokenEl.value);
    }

    xhr.send(params);
  }

  // simple wrapper used by fallback modal so it doesn't crash if config fails
  window.submitFkCreate = function (fieldId) {
    // call the more flexible handler with an empty config
    submitFkCreateWithConfig(fieldId, { 'form-fields': [] });
  }

  // Set up MutationObserver to handle dynamically loaded forms (like in modals)
  fkLog('[FK] Setting up MutationObserver...');
  if (typeof MutationObserver !== 'undefined') {
    var observer = new MutationObserver(function (mutations) {
      fkLog('[FK] MutationObserver triggered, mutations:', mutations.length);
      mutations.forEach(function (mutation) {
        if (mutation.addedNodes.length === 0) return;
        mutation.addedNodes.forEach(function (node) {
          if (node.nodeType === 1) { // Element node
            var dependentSelects = node.querySelectorAll ? node.querySelectorAll('select[data-fk-parent]') : [];
            if (dependentSelects.length > 0) {
              fkLog('[FK] Found dependent selects in dynamically added content:', dependentSelects.length);
              dependentSelects.forEach(function (select) {
                if (!select.dataset.fkInitialized) {
                  select.dataset.fkInitialized = 'true';
                  var parentField = select.getAttribute('data-fk-parent');
                  var parentSelect = document.querySelector('[name="' + parentField + '"]');
                  fkLog('[FK] Setting up dependent select:', select.id, 'parent:', parentField, 'parent found:', !!parentSelect);
                  if (parentSelect) {
                    parentSelect.addEventListener('change', function () {
                      handleParentChange(select, parentSelect.value);
                    });
                    if (parentSelect.value) {
                      handleParentChange(select, parentSelect.value);
                    }
                  }
                }
              });
            }
          }
        });
      });
    });

    observer.observe(document.body, { childList: true, subtree: true });
    fkLog('[FK] MutationObserver started');
  }

})();
