/**
 * Enhanced TabGrid JavaScript - Works with your existing system
 * Automatically enhances all existing tabgrids
 */

window.EnhancedTabGridManager = (function() {
    'use strict';

    let enhancedInstances = new Map();

    class EnhancedTabGridInstance {
        constructor(containerId) {
            this.containerId = containerId;
            this.container = null;
            this.enhanced = false;
        }

        init() {
            this.container = document.getElementById(this.containerId);
            if (!this.container) return;

            this.enhanced = true;
            this.enhanceExistingTabgrid();
            this.setupEventListeners();
        }

        enhanceExistingTabgrid() {
            // Add enhanced class to container
            this.container.classList.add('enhanced-tabgrid');

            // Add enhanced CSS and JS
            this.injectEnhancedAssets();

            // Add sidebar for context
            this.addEnhancedSidebar();

            // Enhance navigation
            this.enhanceTabNavigation();

            // Setup responsive behavior
            this.setupResponsiveBehavior();

            console.log('TabGrid enhanced:', this.containerId);
        }

        injectEnhancedAssets() {
            // Check if enhanced assets are already loaded
            if (!document.querySelector('link[href*="enhanced-tabgrid.css"]')) {
                const css = document.createElement('link');
                css.rel = 'stylesheet';
                css.href = '/css/enhanced-tabgrid.css';
                document.head.appendChild(css);
            }

            if (!window.EnhancedTabGridManagerLoaded) {
                const script = document.createElement('script');
                script.src = '/js/enhanced-tabgrid.js';
                script.onload = () => {
                    window.EnhancedTabGridManagerLoaded = true;
                    console.log('Enhanced TabGrid JavaScript loaded');
                };
                document.head.appendChild(script);
            }
        }

        addEnhancedSidebar() {
            // Find existing nav-tabs
            const navTabs = this.container.querySelector('.nav-tabs');
            const tabContent = this.container.querySelector('.tab-content');

            if (!navTabs || !tabContent) return;

            // Create sidebar
            const sidebar = document.createElement('div');
            sidebar.className = 'enhanced-sidebar';
            sidebar.innerHTML = `
                <div class="enhanced-context-panel">
                    <h6>Current Record</h6>
                    <p>Select a record to see related data</p>
                    <div class="btn-group-sm">
                        <button class="btn btn-outline-primary btn-sm">Change Parent</button>
                        <button class="btn btn-outline-secondary btn-sm">Refresh</button>
                    </div>
                </div>
            </div>
            `;

            // Create enhanced layout
            const layout = document.createElement('div');
            layout.className = 'enhanced-layout';

            // Create main content area
            const mainContent = document.createElement('div');
            mainContent.className = 'enhanced-main-content';

            // Build layout structure
            layout.appendChild(sidebar);
            mainContent.appendChild(navTabs);
            mainContent.appendChild(tabContent);

            // Replace container content
            this.container.innerHTML = '';
            this.container.appendChild(layout);
            this.container.appendChild(mainContent);
        }

        enhanceTabNavigation() {
            const navTabs = this.container.querySelector('.nav-tabs');
            if (!navTabs) return;

            navTabs.classList.add('enhanced-nav-tabs');

            // Add header
            const navHeader = document.createElement('div');
            navHeader.className = 'nav-header';
            navHeader.textContent = 'Related Data';
            navTabs.insertBefore(navHeader, navTabs.firstChild);

            // Enhance tab links
            const navLinks = navTabs.querySelectorAll('.nav-link');
            navLinks.forEach(link => {
                link.classList.add('enhanced-nav-link');
            });
        }

        setupResponsiveBehavior() {
            // Make responsive
            const handleResize = () => {
                const width = window.innerWidth;
                const isMobile = width < 768;
                const sidebar = this.container.querySelector('.enhanced-sidebar');
                const layout = this.container.querySelector('.enhanced-layout');
                const mainContent = this.container.querySelector('.enhanced-main-content');

                if (!sidebar || !layout || !mainContent) return;

                if (isMobile) {
                    layout.classList.add('mobile-layout');
                    sidebar.classList.add('mobile-sidebar');
                    mainContent.classList.add('mobile-main');
                } else {
                    layout.classList.remove('mobile-layout');
                    sidebar.classList.remove('mobile-sidebar');
                    mainContent.classList.remove('mobile-main');
                }
            };

            window.addEventListener('resize', handleResize);
            handleResize(); // Call once initially
        }

        setupEventListeners() {
            // Enhanced tab switching
            this.container.addEventListener('click', (e) => {
                if (e.target.matches('.enhanced-nav-link')) {
                    this.handleEnhancedTabSwitch(e);
                } else if (e.target.matches('.enhanced-sidebar button')) {
                    this.handleSidebarAction(e);
                }
            });

            // Keyboard navigation
            this.container.addEventListener('keydown', (e) => {
                this.handleKeyboardNavigation(e);
            });
        }

        handleEnhancedTabSwitch(event) {
            console.log('Enhanced tab switched');
            // Add visual feedback
            const allPanes = this.container.querySelectorAll('.tab-pane');
            const targetPane = document.querySelector(event.target.getAttribute('data-bs-target'));

            allPanes.forEach(pane => {
                if (pane === targetPane) {
                    pane.classList.add('show', 'active', 'enhanced-active');
                } else {
                    pane.classList.remove('show', 'active', 'enhanced-active');
                }
            });

            const allLinks = this.container.querySelectorAll('.enhanced-nav-link');
            allLinks.forEach(link => {
                if (link === event.target) {
                    link.classList.add('active');
                } else {
                    link.classList.remove('active');
                }
            });
        }

        handleSidebarAction(event) {
            const action = event.target.textContent.trim();
            console.log('Sidebar action:', action);

            switch (action) {
                case 'Change Parent':
                    this.showParentSelector();
                    break;
                case 'Refresh':
                    this.refreshCurrentTab();
                    break;
            }
        }

        handleKeyboardNavigation(event) {
            const navLinks = this.container.querySelectorAll('.enhanced-nav-link');
            const currentIndex = Array.from(navLinks).findIndex(link => link.classList.contains('active'));

            switch (event.key) {
                case 'ArrowLeft':
                    this.switchToTab(Math.max(0, currentIndex - 1));
                    break;
                case 'ArrowRight':
                    this.switchToTab(Math.min(navLinks.length - 1, currentIndex + 1));
                    break;
                case 'Home':
                    this.switchToTab(0);
                    break;
                case 'End':
                    this.switchToTab(navLinks.length - 1);
                    break;
            }
        }

        switchToTab(index) {
            const navLinks = this.container.querySelectorAll('.enhanced-nav-link');
            if (navLinks[index]) {
                navLinks[index].click();
            }
        }

        showParentSelector() {
            console.log('Parent selector would be shown');
            // This would integrate with your existing modal system
        }

        refreshCurrentTab() {
            console.log('Current tab refreshed');
            const activePane = this.container.querySelector('.enhanced-active');
            if (activePane) {
                // Show loading state
                activePane.innerHTML = '<div class="loading-placeholder"><div>Loading...</div>';
                
                // Simulate refresh
                setTimeout(() => {
                    // In real implementation, this would reload tab content
                    console.log('Tab content would be reloaded');
                }, 1000);
            }
        }
    }

    // Public API
    return {
        init: function(containerId) {
            if (!containerId) return;

            if (enhancedInstances.has(containerId)) {
                return enhancedInstances.get(containerId);
            }

            const instance = new EnhancedTabGridInstance(containerId);
            instance.init();
            enhancedInstances.set(containerId, instance);
            return instance;
        },

        enhanceExisting: function() {
            // Enhance all existing tabgrids on page load
            document.addEventListener('DOMContentLoaded', function() {
                const tabgrids = document.querySelectorAll('[id^="tabgrid-"]');
                
                tabgrids.forEach(tabgrid => {
                    const containerId = tabgrid.id;
                    if (!containerId.startsWith('enhanced-')) {
                        window.EnhancedTabGridManager.init(containerId);
                    }
                });

                console.log('Enhanced all existing tabgrids');
            });
        },

        getEnhancedInstance: function(containerId) {
            return enhancedInstances.get(containerId);
        },

        isEnhanced: function(containerId) {
            const instance = enhancedInstances.get(containerId);
            return instance ? instance.enhanced : false;
        }
    };
})();

// Auto-enhance all existing tabgrids when DOM is ready
window.EnhancedTabGridManager.enhanceExisting();