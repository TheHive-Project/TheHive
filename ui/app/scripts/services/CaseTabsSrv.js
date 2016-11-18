(function() {
    'use strict';
    angular.module('theHiveServices').factory('CaseTabsSrv', function() {
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

        return {

            initTabs: function() {
                angular.forEach(tabs, function(tab, key) {
                    if (tab.closable === true) {
                        delete tabs[key];
                    }
                });
            },

            getTabs: function() {
                return tabs;
            },

            getTab: function(name) {
                return tabs[name];
            },

            activateTab: function(tab) {
                angular.forEach(tabs, function(tab) {
                    tab.active = false;
                });

                if (tabs[tab]) {
                    tabs[tab].active = true;
                } else {
                    tabs.details.active = true;
                }
            },

            addTab: function(tab, options) {
                options.closable = true;

                tabs[tab] = options;
            },

            removeTab: function(tab) {
                var tabItem = tabs[tab];

                if(!tabItem) {
                    return;
                }

                var currentIsActive = tabItem.active;

                delete tabs[tab];

                if (currentIsActive) {
                    return true;
                } else {
                    return false;
                }

            }
        };
    });
})();
