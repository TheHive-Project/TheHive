(function() {
    'use strict';

    angular
        .module('theHiveControllers')
        .controller('DashboardViewCtrl', function($q, $uibModal, DashboardSrv, NotificationSrv, ModalUtilsSrv, dashboard, metadata) {
            var self = this;

            this.dashboard = dashboard;
            this.definition = JSON.parse(dashboard.definition) || {
                items: [
                    {
                        type: 'container',
                        items: []
                    }
                ]
            };

            this.options = {
                dashboardAllowedTypes: ['container'],
                containerAllowedTypes: ['bar', 'line', 'donut'],
                maxColumns: 3,
                cls: {
                    container: 'fa-window-maximize',
                    bar: 'fa-bar-chart',
                    donut: 'fa-pie-chart',
                    line: 'fa-line-chart'
                },
                labels: {
                    container: 'Row',
                    bar: 'Bar',
                    donut: 'Donut',
                    line: 'Line'
                },
                editLayout: false
            };
            this.toolbox = DashboardSrv.toolbox;

            this.metadata = metadata;

            this.removeContainer = function(index) {
                var row = this.definition.items[index];

                var promise;
                if(row.items.length === 0) {
                    // If the container is empty, don't ask for confirmation
                    promise = $q.resolve();
                } else {
                    promise = ModalUtilsSrv.confirm('Remove widget', 'Are you sure you want to remove this item', {
                        okText: 'Yes, remove it',
                        flavor: 'danger'
                    })
                }

                promise.then(function() {
                    self.definition.items.splice(index, 1)
                });
            }

            this.saveDashboard = function() {
                var copy = _.pick(this.dashboard, 'title', 'description', 'status');
                copy.definition = angular.toJson(this.definition);

                DashboardSrv.update(this.dashboard.id, copy)
                    .then(function(response) {
                        self.options.editLayout = false;
                        NotificationSrv.log('The dashboard has been successfully updated', 'success');
                    })
                    .catch(function(err) {
                        NotificationSrv.error('DashboardEditCtrl', err.data, err.status);
                    })
            }

            this.removeItem = function(rowIndex, colIndex) {

                ModalUtilsSrv.confirm('Remove widget', 'Are you sure you want to remove this item', {
                    okText: 'Yes, remove it',
                    flavor: 'danger'
                }).then(function() {
                    var row = self.definition.items[rowIndex];
                    row.items.splice(colIndex, 1);
                });

            }

        });
})();
