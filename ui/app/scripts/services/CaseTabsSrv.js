(function() {
    'use strict';
    angular.module('theHiveServices').service('CaseTabsSrv', function() {

        var tabs = {
            'details': {
                name: 'details',
                active: true,
                label: 'Summary',
                state: 'app.case.details'
            },
            'tasks': {
                name: 'tasks',
                active: false,
                label: 'Tasks',
                state: 'app.case.tasks'
            },
            'observables': {
                name: 'observables',
                active: false,
                label: 'Observables',
                state: 'app.case.observables'
            }
        };

        this.activeIndex = 0;

        this.initTabs = function() {
            angular.forEach(tabs, function(tab, key) {
                if (tab.closable === true) {
                    delete tabs[key];
                }
            });
        };

        this.getTabs = function() {
            return tabs;
        };

        this.getTab = function(name) {
            return tabs[name];
        };

        this.activateTab = function(tab) {
            angular.forEach(tabs, function(tab) {
                tab.active = false;
            });

            if (tabs[tab]) {
                tabs[tab].active = true;
                this.activeIndex = Object.keys(tabs).indexOf(tab);
            } else {
                tabs.details.active = true;
                this.activeIndex = 0;
            }
        };

        this.addTab = function(tab, options) {
            options.closable = true;

            tabs[tab] = options;
            this.activeIndex = Object.keys(tabs).length - 1;
        };

        this.removeTab = function(tab) {
            var tabItem = tabs[tab];

            if (!tabItem) {
                return;
            }

            var currentIsActive = tabItem.active;

            delete tabs[tab];

            if (currentIsActive) {
                return true;
            } else {
                return false;
            }

        };

    });
})();
