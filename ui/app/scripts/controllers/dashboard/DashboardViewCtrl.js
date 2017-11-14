(function() {
    'use strict';

    angular
        .module('theHiveControllers')
        .controller('DashboardViewCtrl', function($scope, $q, $timeout, $uibModal, DashboardSrv, NotificationSrv, ModalUtilsSrv, UtilsSrv, dashboard, metadata) {
            var self = this;

            this.dashboard = dashboard;
            this.definition = JSON.parse(dashboard.definition) || {
                period: 'all',
                items: [
                    {
                        type: 'container',
                        items: []
                    }
                ]
            };

            this.options = {
                dashboardAllowedTypes: ['container'],
                containerAllowedTypes: ['bar', 'line', 'donut', 'counter'],
                maxColumns: 3,
                cls: DashboardSrv.typeClasses,
                labels: {
                    container: 'Row',
                    bar: 'Bar',
                    donut: 'Donut',
                    line: 'Line',
                    counter: 'Counter'
                },
                editLayout: !_.find(this.definition.items, function(row) {
                    return row.items.length > 0;
                })
            };
            this.toolbox = DashboardSrv.toolbox;
            this.dashboardPeriods = DashboardSrv.dashboardPeriods;

            this.metadata = metadata;

            this.buildDashboardPeriodFilter = function(period) {
                return period === 'custom' ?
                    DashboardSrv.buildPeriodQuery(period, 'createdAt', this.definition.customPeriod.fromDate, this.definition.customPeriod.toDate) :
                    DashboardSrv.buildPeriodQuery(period, 'createdAt');
            }

            this.periodFilter = this.buildDashboardPeriodFilter(this.definition.period);

            this.applyPeriod = function(period) {
                this.definition.period = period;
                this.periodFilter = this.buildDashboardPeriodFilter(period);

                $scope.$broadcast('refresh-chart', this.periodFilter);
            }

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

                    $timeout(function() {
                        $scope.$broadcast('resize-chart-' + rowIndex);
                    }, 0);
                });

            }

            this.itemInserted = function(item, rows, rowIndex, index) {
                if(!item.id){
                    item.id = UtilsSrv.guid();
                }

                for(var i=0; i < rows.length; i++) {
                    $scope.$broadcast('resize-chart-' + i);
                }

                if (this.options.containerAllowedTypes.indexOf(item.type) !== -1 && !item.options.entity) {
                    // The item is a widget
                    $timeout(function() {
                        $scope.$broadcast('edit-chart-' + item.id);
                    }, 0);
                }

                return item;
            }

            this.itemDragStarted = function(colIndex, row) {
                row.items.splice(colIndex, 1);
            }

            this.exportDashboard = function() {
                DashboardSrv.exportDashboard(this.dashboard);
            }

        });
})();
