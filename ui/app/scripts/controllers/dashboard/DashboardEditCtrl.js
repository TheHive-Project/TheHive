(function() {
    'use strict';

    angular
        .module('theHiveControllers')
        .controller('DashboardEditCtrl', function(DashboardSrv, NotificationSrv, dashboard) {
            this.dashboard = dashboard;
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

            this.toolbox = DashboardSrv.toolbox;

            this.definition = JSON.parse(dashboard.definition) || {
                items: [
                    {
                        type: 'container',
                        items: []
                    }
                ]
            };

            this.removeContainer = function(index) {
                //this.dashboard.items.remove(item);

                this.definition.items.splice(index, 1)
            }

            this.saveDashboard = function() {
                var copy = _.pick(this.dashboard, 'title', 'description', 'status');
                copy.definition = angular.toJson(this.definition);

                DashboardSrv.update(this.dashboard.id, copy)
                    .then(function(response) {
                        NotificationSrv.log('The dashboard has been successfully updated', 'success');
                    })
                    .catch(function(err) {
                        NotificationSrv.error('DashboardEditCtrl', err.data, err.status);
                    })
            }
        });
})();
