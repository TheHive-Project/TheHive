(function() {
    'use strict';

    angular
        .module('theHiveControllers')
        .controller('DashboardViewCtrl', function($scope, $q, $interval, $timeout, $uibModal, AuthenticationSrv, DashboardSrv, NotificationSrv, ModalUtilsSrv, UtilsSrv, dashboard, metadata) {
            var self = this;

            this.currentUser = AuthenticationSrv.currentUser;
            this.createdBy = dashboard.createdBy;
            this.dashboardStatus = dashboard.dashboardStatus;
            this.metadata = metadata;
            this.toolbox = DashboardSrv.toolbox;
            this.dashboardPeriods = DashboardSrv.dashboardPeriods;
            this.autoRefresh = null;
            this.authRefreshRunner = null;

            this.buildDashboardPeriodFilter = function(period) {
                return period === 'custom' ?
                    DashboardSrv.buildPeriodQuery(period, 'createdAt', this.definition.customPeriod.fromDate, this.definition.customPeriod.toDate) :
                    DashboardSrv.buildPeriodQuery(period, 'createdAt');
            };

            this.loadDashboard = function(dashboard) {
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
                this.periodFilter = this.buildDashboardPeriodFilter(this.definition.period);
            };

            this.loadDashboard(dashboard);

            $scope.$watch('$vm.autoRefresh', function(value) {
                if(value === self.authRefreshRunner || self.options.editLayout === true) {
                    return;
                }

                if(value === null) {
                    $interval.cancel(self.authRefreshRunner);
                } else {
                    $interval.cancel(self.authRefreshRunner);
                    self.authRefreshRunner = $interval(function() {
                        $scope.$broadcast('refresh-chart', self.periodFilter);
                    }, value * 1000);
                }
            });



            this.canEditDashboard = function() {
                return (this.createdBy === this.currentUser.login) || this.dashboardStatus === 'Shared';                
            };

            this.options = {
                dashboardAllowedTypes: ['container'],
                containerAllowedTypes: ['bar', 'line', 'donut', 'counter', 'text', 'multiline'],
                maxColumns: 3,
                cls: DashboardSrv.typeClasses,
                labels: {
                    container: 'Row',
                    bar: 'Bar',
                    donut: 'Donut',
                    line: 'Line',
                    counter: 'Counter',
                    text: 'Text',
                    multiline: 'Multi Lines'
                },
                editLayout: !_.find(this.definition.items, function(row) {
                    return row.items.length > 0;
                }) && this.canEditDashboard()
            };

            this.applyPeriod = function(period) {
                this.definition.period = period;
                this.periodFilter = this.buildDashboardPeriodFilter(period);

                $scope.$broadcast('refresh-chart', this.periodFilter);
            };

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
                    });
                }

                promise.then(function() {
                    self.definition.items.splice(index, 1);
                });
            };

            this.saveDashboard = function() {
                var copy = _.pick(this.dashboard, 'title', 'description', 'status');
                copy.definition = angular.toJson(this.definition);

                DashboardSrv.update(this.dashboard.id, copy)
                    .then(function(/*response*/) {
                        self.options.editLayout = false;
                        self.resizeCharts();
                        NotificationSrv.log('The dashboard has been successfully updated', 'success');
                    })
                    .catch(function(err) {
                        NotificationSrv.error('DashboardEditCtrl', err.data, err.status);
                    });
            };

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

            };

            this.itemInserted = function(item, rows/*, rowIndex, index*/) {
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
            };

            this.itemDragStarted = function(colIndex, row) {
                row.items.splice(colIndex, 1);
            };

            this.exportDashboard = function() {
                DashboardSrv.exportDashboard(this.dashboard);
            };

            this.resizeCharts = function() {
                $timeout(function() {
                    for(var i=0; i < self.definition.items.length; i++) {
                        $scope.$broadcast('resize-chart-' + i);
                    }
                }, 100);
            };

            this.enableEditMode = function() {
                this.options.editLayout = true;
                this.resizeCharts();
            };

            this.enableViewMode = function() {
                DashboardSrv.get(this.dashboard.id)
                    .then(function(response) {
                        self.loadDashboard(response.data);
                        self.options.editLayout = false;
                        self.resizeCharts();
                    }, function(err) {
                        NotificationSrv.error('DashboardViewCtrl', err.data, err.status);
                    });
            };


        });
})();
