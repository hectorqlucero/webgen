/**
 * Professional TabGrid Manager - Modular JavaScript Architecture
 * Provides enterprise-grade grid management with modern UX patterns
 * Compatible with existing Clojure/Hiccup backend
 */

// Main namespace for TabGrid functionality
window.TabGridManager = (function() {
    'use strict';

    // Private variables
    let instances = new Map();
    let eventBus = null;
    let config = {};

    // Default configuration
    const DEFAULT_CONFIG = {
        ui: {
            style: 'enhanced-tabgrid',
            layout: {
                splitView: true,
                breadcrumbs: true,
                quickSearch: true,
                responsiveBreakpoints: {
                    mobile: 768,
                    tablet: 1024,
                    desktop: 1200
                }
            },
            animation: {
                duration: 300,
                easing: 'ease-in-out'
            },
            mobile: {
                compactMode: true,
                swipeGestures: true
            }
        },
        behavior: {
            lazyLoading: true,
            cacheEnabled: true,
            cacheTimeout: 300000, // 5 minutes
            autoRefresh: false,
            confirmBeforeDelete: true
        },
        accessibility: {
            keyboardNavigation: true,
            screenReaderSupport: true,
            highContrast: false,
            focusManagement: true
        }
    };

    // Event Bus Implementation
    class EventBus {
        constructor() {
            this.listeners = new Map();
        }

        on(event, callback) {
            if (!this.listeners.has(event)) {
                this.listeners.set(event, []);
            }
            this.listeners.get(event).push(callback);
        }

        off(event, callback) {
            if (this.listeners.has(event)) {
                const callbacks = this.listeners.get(event);
                const index = callbacks.indexOf(callback);
                if (index > -1) {
                    callbacks.splice(index, 1);
                }
            }
        }

        emit(event, data) {
            if (this.listeners.has(event)) {
                this.listeners.get(event).forEach(callback => {
                    try {
                        callback(data);
                    } catch (error) {
                        console.error(`Error in event handler for ${event}:`, error);
                    }
                });
            }
        }
    }

    // Cache Manager
    class CacheManager {
        constructor() {
            this.cache = new Map();
            this.timestamps = new Map();
        }

        get(key) {
            if (this.cache.has(key)) {
                const timestamp = this.timestamps.get(key);
                if (Date.now() - timestamp < config.behavior.cacheTimeout) {
                    return this.cache.get(key);
                }
                this.delete(key);
            }
            return null;
        }

        set(key, value) {
            this.cache.set(key, value);
            this.timestamps.set(key, Date.now());
        }

        delete(key) {
            this.cache.delete(key);
            this.timestamps.delete(key);
        }

        clear() {
            this.cache.clear();
            this.timestamps.clear();
        }
    }

    // Enhanced TabGrid Instance
    class TabGridInstance {
        constructor(containerId, instanceConfig) {
            this.id = containerId;
            this.container = document.getElementById(containerId);
            this.config = { ...DEFAULT_CONFIG, ...instanceConfig };
            this.cache = new CacheManager();
            this.currentTab = null;
            this.parentContext = null;
            this.dataTables = new Map();
            this.loadingStates = new Map();
            
            this.init();
        }

        init() {
            if (!this.container) {
                console.error(`TabGrid container ${this.id} not found`);
                return;
            }

            this.setupUI();
            this.bindEvents();
            this.loadInitialData();
            this.setupAccessibility();
        }

        setupUI() {
            // Add responsive container classes
            this.container.classList.add('tabgrid-enhanced', 'tabgrid-container');
            
            // Setup layout structure
            this.createLayout();
            this.createBreadcrumbs();
            this.createQuickSearch();
            this.enhanceTabNavigation();
            
            // Apply responsive design
            this.applyResponsiveDesign();
        }

        createLayout() {
            const layout = this.config.ui.layout;
            
            if (layout.splitView) {
                this.createSplitViewLayout();
            } else {
                this.createTabbedLayout();
            }
        }

        createSplitViewLayout() {
            const layoutHTML = `
                <div class="tabgrid-layout-split">
                    <div class="tabgrid-sidebar">
                        <div class="tabgrid-context-panel">
                            <!-- Parent context will be rendered here -->
                        </div>
                        <div class="tabgrid-navigation">
                            <!-- Enhanced navigation -->
                        </div>
                    </div>
                    <div class="tabgrid-main-content">
                        <div class="tabgrid-tabs-container">
                            <!-- Tabs will be enhanced here -->
                        </div>
                        <div class="tabgrid-content-area">
                            <!-- Content will be rendered here -->
                        </div>
                    </div>
                </div>
            `;
            
            this.container.innerHTML = layoutHTML;
            this.layoutElements = {
                sidebar: this.container.querySelector('.tabgrid-sidebar'),
                contextPanel: this.container.querySelector('.tabgrid-context-panel'),
                navigation: this.container.querySelector('.tabgrid-navigation'),
                mainContent: this.container.querySelector('.tabgrid-main-content'),
                tabsContainer: this.container.querySelector('.tabgrid-tabs-container'),
                contentArea: this.container.querySelector('.tabgrid-content-area')
            };
        }

        createTabbedLayout() {
            // Enhanced version of existing tabbed layout
            this.enhanceExistingTabs();
        }

        createBreadcrumbs() {
            if (!this.config.ui.layout.breadcrumbs) return;
            
            const breadcrumbContainer = document.createElement('div');
            breadcrumbContainer.className = 'tabgrid-breadcrumbs';
            breadcrumbContainer.setAttribute('aria-label', 'Breadcrumb navigation');
            
            this.container.insertBefore(breadcrumbContainer, this.container.firstChild);
            this.breadcrumbsElement = breadcrumbContainer;
        }

        createQuickSearch() {
            if (!this.config.ui.layout.quickSearch) return;
            
            const searchHTML = `
                <div class="tabgrid-quick-search">
                    <div class="search-container">
                        <input type="text" 
                               class="form-control search-input" 
                               placeholder="Quick search..." 
                               aria-label="Search records">
                        <button class="btn btn-outline-secondary search-btn" type="button">
                            <i class="bi bi-search"></i>
                        </button>
                    </div>
                    <div class="search-results" style="display: none;">
                        <!-- Search results will appear here -->
                    </div>
                </div>
            `;
            
            const searchContainer = document.createElement('div');
            searchContainer.innerHTML = searchHTML;
            this.container.appendChild(searchContainer.firstElementChild);
            
            this.setupQuickSearchEvents();
        }

        enhanceTabNavigation() {
            const tabs = this.container.querySelectorAll('[data-bs-toggle="tab"]');
            
            tabs.forEach(tab => {
                // Add loading indicators
                const loadingIndicator = document.createElement('span');
                loadingIndicator.className = 'tab-loading-indicator';
                loadingIndicator.innerHTML = '<i class="bi bi-arrow-repeat"></i>';
                loadingIndicator.style.display = 'none';
                tab.appendChild(loadingIndicator);
                
                // Enhance with better accessibility
                tab.setAttribute('role', 'tab');
                tab.setAttribute('tabindex', '0');
            });
        }

        bindEvents() {
            // Tab switching events
            this.container.addEventListener('shown.bs.tab', (e) => {
                this.handleTabSwitch(e.target);
            });

            // Parent selection events
            this.container.addEventListener('parent-selected', (e) => {
                this.handleParentSelection(e.detail);
            });

            // Search events
            this.container.addEventListener('search', (e) => {
                this.handleSearch(e.detail.query);
            });

            // Responsive events
            window.addEventListener('resize', () => {
                this.handleResize();
            });

            // Keyboard navigation
            if (this.config.accessibility.keyboardNavigation) {
                this.setupKeyboardNavigation();
            }
        }

        handleTabSwitch(tabElement) {
            const targetPaneId = tabElement.getAttribute('data-bs-target') || 
                              tabElement.getAttribute('href');
            
            if (!targetPaneId) return;

            const targetPane = document.querySelector(targetPaneId);
            if (!targetPane) return;

            // Show loading state
            this.showTabLoading(tabElement);

            // Load tab content if needed
            this.loadTabContent(targetPaneId).then(() => {
                this.hideTabLoading(tabElement);
                this.currentTab = targetPaneId;
                
                // Update accessibility
                this.updateAccessibility(tabElement, targetPane);
                
                // Emit event
                eventBus.emit('tab-changed', {
                    tabId: targetPaneId,
                    tabElement: tabElement,
                    paneElement: targetPane
                });
            });
        }

        loadTabContent(tabId) {
            return new Promise((resolve, reject) => {
                // Check cache first
                const cacheKey = `tab-${tabId}`;
                if (this.config.behavior.cacheEnabled) {
                    const cached = this.cache.get(cacheKey);
                    if (cached) {
                        resolve(cached);
                        return;
                    }
                }

                // Load content dynamically if needed
                const tabPane = document.querySelector(tabId);
                if (!tabPane) {
                    reject(new Error(`Tab pane ${tabId} not found`));
                    return;
                }

                // Check if content needs to be loaded
                if (tabPane.dataset.needsLoading === 'true') {
                    this.loadRemoteContent(tabId)
                        .then(resolve)
                        .catch(reject);
                } else {
                    resolve(tabPane);
                }
            });
        }

        loadRemoteContent(tabId) {
            const url = `/api/tab-content/${tabId.replace('#', '')}`;
            
            return fetch(url, {
                headers: {
                    'Accept': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest'
                }
            })
            .then(response => response.json())
            .then(data => {
                const tabPane = document.querySelector(tabId);
                if (tabPane && data.html) {
                    tabPane.innerHTML = data.html;
                    this.initializeTabDataTable(tabId, data.dataTablesConfig);
                    
                    if (this.config.behavior.cacheEnabled) {
                        this.cache.set(`tab-${tabId}`, tabPane);
                    }
                }
                return tabPane;
            });
        }

        initializeTabDataTable(tabId, config) {
            if (!config) return;

            const tabPane = document.querySelector(tabId);
            const table = tabPane.querySelector('table.dataTable');
            
            if (table && !$.fn.DataTable.isDataTable(table)) {
                const dataTable = $(table).DataTable(config);
                this.dataTables.set(tabId, dataTable);
            }
        }

        showTabLoading(tabElement) {
            const loadingIndicator = tabElement.querySelector('.tab-loading-indicator');
            if (loadingIndicator) {
                loadingIndicator.style.display = 'inline-block';
            }
        }

        hideTabLoading(tabElement) {
            const loadingIndicator = tabElement.querySelector('.tab-loading-indicator');
            if (loadingIndicator) {
                loadingIndicator.style.display = 'none';
            }
        }

        handleParentSelection(detail) {
            this.parentContext = detail;
            
            // Update context panel
            this.updateContextPanel(detail);
            
            // Update all child grids
            this.updateChildGrids(detail);
            
            // Update breadcrumbs
            this.updateBreadcrumbs(detail);
            
            // Emit event
            eventBus.emit('context-updated', detail);
        }

        updateContextPanel(context) {
            if (!this.layoutElements?.contextPanel) return;

            const contextHTML = this.renderContextPanel(context);
            this.layoutElements.contextPanel.innerHTML = contextHTML;
        }

        renderContextPanel(context) {
            return `
                <div class="context-panel-content">
                    <div class="context-header">
                        <h5 class="context-title">${context.title || 'Current Record'}</h5>
                    </div>
                    <div class="context-details">
                        ${this.renderContextDetails(context)}
                    </div>
                    <div class="context-actions">
                        ${this.renderContextActions(context)}
                    </div>
                </div>
            `;
        }

        renderContextDetails(context) {
            if (!context.record) return '';

            return Object.entries(context.record)
                .filter(([key, value]) => value && key !== 'id')
                .slice(0, 5) // Show first 5 key fields
                .map(([key, value]) => `
                    <div class="context-detail">
                        <span class="detail-label">${this.formatLabel(key)}:</span>
                        <span class="detail-value">${value}</span>
                    </div>
                `).join('');
        }

        renderContextActions(context) {
            return `
                <div class="btn-group-vertical w-100" role="group">
                    <button class="btn btn-outline-primary btn-sm" onclick="TabGridManager.changeParent('${this.id}')">
                        <i class="bi bi-arrow-left"></i> Change Parent
                    </button>
                    <button class="btn btn-outline-secondary btn-sm" onclick="TabGridManager.refreshContext('${this.id}')">
                        <i class="bi bi-arrow-clockwise"></i> Refresh
                    </button>
                </div>
            `;
        }

        updateChildGrids(context) {
            // Update all child grids with new parent context
            this.dataTables.forEach((dataTable, tabId) => {
                if (this.isChildGrid(tabId)) {
                    // Apply parent filter
                    this.applyParentFilter(dataTable, context.id);
                }
            });
        }

        applyParentFilter(dataTable, parentId) {
            // Apply parent filter to DataTable
            if (parentId && dataTable.column('parent_id')) {
                dataTable
                    .column('parent_id')
                    .search(parentId)
                    .draw();
            }
        }

        isChildGrid(tabId) {
            // Logic to determine if this is a child grid
            return tabId.includes('child') || tabId.includes('subgrid');
        }

        setupKeyboardNavigation() {
            this.container.addEventListener('keydown', (e) => {
                switch (e.key) {
                    case 'ArrowLeft':
                        this.navigateTabs('previous');
                        break;
                    case 'ArrowRight':
                        this.navigateTabs('next');
                        break;
                    case 'Home':
                        this.navigateTabs('first');
                        break;
                    case 'End':
                        this.navigateTabs('last');
                        break;
                }
            });
        }

        navigateTabs(direction) {
            const tabs = Array.from(this.container.querySelectorAll('[data-bs-toggle="tab"]'));
            const currentIndex = tabs.findIndex(tab => tab.classList.contains('active'));
            
            let nextIndex;
            switch (direction) {
                case 'previous':
                    nextIndex = currentIndex > 0 ? currentIndex - 1 : tabs.length - 1;
                    break;
                case 'next':
                    nextIndex = currentIndex < tabs.length - 1 ? currentIndex + 1 : 0;
                    break;
                case 'first':
                    nextIndex = 0;
                    break;
                case 'last':
                    nextIndex = tabs.length - 1;
                    break;
            }
            
            if (tabs[nextIndex]) {
                tabs[nextIndex].click();
            }
        }

        applyResponsiveDesign() {
            const width = window.innerWidth;
            const breakpoints = this.config.ui.layout.responsiveBreakpoints;
            
            if (width < breakpoints.mobile) {
                this.container.classList.add('mobile-layout');
                this.container.classList.remove('tablet-layout', 'desktop-layout');
            } else if (width < breakpoints.tablet) {
                this.container.classList.add('tablet-layout');
                this.container.classList.remove('mobile-layout', 'desktop-layout');
            } else {
                this.container.classList.add('desktop-layout');
                this.container.classList.remove('mobile-layout', 'tablet-layout');
            }
        }

        handleResize() {
            clearTimeout(this.resizeTimer);
            this.resizeTimer = setTimeout(() => {
                this.applyResponsiveDesign();
                eventBus.emit('resize', {
                    width: window.innerWidth,
                    height: window.innerHeight
                });
            }, 250);
        }

        setupAccessibility() {
            if (!this.config.accessibility.screenReaderSupport) return;

            // Add ARIA labels and roles
            this.container.setAttribute('role', 'application');
            this.container.setAttribute('aria-label', 'Data grid interface');

            // Setup live regions for dynamic content
            const liveRegion = document.createElement('div');
            liveRegion.setAttribute('aria-live', 'polite');
            liveRegion.setAttribute('aria-atomic', 'true');
            liveRegion.className = 'sr-only';
            this.container.appendChild(liveRegion);
        }

        updateAccessibility(tabElement, paneElement) {
            // Update ARIA attributes
            tabElement.setAttribute('aria-selected', 'true');
            paneElement.setAttribute('aria-expanded', 'true');

            // Update focus management
            if (this.config.accessibility.focusManagement) {
                tabElement.focus();
            }
        }

        setupQuickSearchEvents() {
            const searchInput = this.container.querySelector('.search-input');
            const searchBtn = this.container.querySelector('.search-btn');
            const searchResults = this.container.querySelector('.search-results');

            if (!searchInput) return;

            let searchTimer;

            searchInput.addEventListener('input', (e) => {
                clearTimeout(searchTimer);
                searchTimer = setTimeout(() => {
                    this.performSearch(e.target.value);
                }, 300);
            });

            searchInput.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    searchResults.style.display = 'none';
                    searchInput.blur();
                }
            });

            searchBtn.addEventListener('click', () => {
                this.performSearch(searchInput.value);
            });

            // Hide results when clicking outside
            document.addEventListener('click', (e) => {
                if (!this.container.contains(e.target)) {
                    searchResults.style.display = 'none';
                }
            });
        }

        performSearch(query) {
            if (!query.trim()) {
                this.hideSearchResults();
                return;
            }

            // Emit search event
            eventBus.emit('search', { query: query.trim() });
        }

        hideSearchResults() {
            const results = this.container.querySelector('.search-results');
            if (results) {
                results.style.display = 'none';
            }
        }

        updateBreadcrumbs(context) {
            if (!this.breadcrumbsElement) return;

            const breadcrumbs = this.generateBreadcrumbs(context);
            this.breadcrumbsElement.innerHTML = breadcrumbs;
        }

        generateBreadcrumbs(context) {
            const items = [
                { label: 'Home', href: '/' },
                ...(context.path || []),
                { label: context.title, active: true }
            ];

            return `
                <nav aria-label="breadcrumb">
                    <ol class="breadcrumb">
                        ${items.map((item, index) => `
                            <li class="breadcrumb-item ${item.active ? 'active' : ''}">
                                ${item.active ? 
                                    item.label : 
                                    `<a href="${item.href}">${item.label}</a>`
                                }
                            </li>
                        `).join('')}
                    </ol>
                </nav>
            `;
        }

        formatLabel(key) {
            return key.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
        }

        // Public methods
        refresh() {
            eventBus.emit('refresh-requested', { instanceId: this.id });
        }

        destroy() {
            // Clean up DataTables
            this.dataTables.forEach(dataTable => {
                if (dataTable && dataTable.destroy) {
                    dataTable.destroy();
                }
            });

            // Clean up cache
            this.cache.clear();

            // Remove event listeners
            eventBus.off('tab-changed', this.handleTabSwitch);
            eventBus.off('parent-selected', this.handleParentSelection);
        }
    }

    // Public API
    return {
        init: function(containerId, instanceConfig = {}) {
            if (instances.has(containerId)) {
                console.warn(`TabGrid instance ${containerId} already exists`);
                return instances.get(containerId);
            }

            if (!eventBus) {
                eventBus = new EventBus();
            }

            config = { ...DEFAULT_CONFIG, ...instanceConfig };
            
            const instance = new TabGridInstance(containerId, instanceConfig);
            instances.set(containerId, instance);
            
            return instance;
        },

        getInstance: function(containerId) {
            return instances.get(containerId);
        },

        destroy: function(containerId) {
            const instance = instances.get(containerId);
            if (instance) {
                instance.destroy();
                instances.delete(containerId);
            }
        },

        destroyAll: function() {
            instances.forEach((instance, id) => {
                instance.destroy();
            });
            instances.clear();
        },

        // Utility methods
        changeParent: function(containerId) {
            // Trigger parent selection modal
            const modal = document.querySelector('.parent-selection-modal');
            if (modal) {
                const bsModal = new bootstrap.Modal(modal);
                bsModal.show();
            }
        },

        refreshContext: function(containerId) {
            const instance = instances.get(containerId);
            if (instance && instance.parentContext) {
                instance.handleParentSelection(instance.parentContext);
            }
        },

        // Configuration
        setGlobalConfig: function(newConfig) {
            config = { ...config, ...newConfig };
        },

        getGlobalConfig: function() {
            return { ...config };
        }
    };
})();