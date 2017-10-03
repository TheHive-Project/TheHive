(function() {
    'use strict';

    angular
        .module('theHiveControllers')
        .controller('DashboardEditCtrl', function(dashboard) {
            this.options = {
                dashboardAllowedTypes: ['container'],
                containerAllowedTypes: ['bar', 'line', 'donut'],
                maxColumns: 3,
                cls: {
                    container: 'fa-window-maximize',
                    bar: 'fa-bar-chart',
                    donut: 'fa-pie-chart',
                    line: 'fa-line-chart'
                }
            };

            this.toolbox = [
                {
                    id: 1,
                    type: 'container',
                    items: []
                },
                {
                    id: 2,
                    type: 'bar'
                },
                {
                    id: 3,
                    type: 'line'
                },
                {
                    id: 4,
                    type: 'donut'
                }
            ];

            this.dashboard = JSON.parse(dashboard.definition) || {
                items: [
                    {
                        type: 'container',
                        items: []
                    }
                ]
            };
        });
})();
