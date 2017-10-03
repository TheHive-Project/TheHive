(function() {
    'use strict';

    angular
        .module('theHiveControllers')
        .controller('DashboardsCtrl', function($scope, $uibModal, PSearchSrv, NotificationSrv, DashboardSrv, AuthenticationSrv) {
            //this.dashboards = dashboards.data || [];
            var self = this;

            this.dashboards = PSearchSrv('any', 'dashboard', {
                scope: $scope,
                baseFilter: {
                    _and: [
                        {
                            _not: { status: 'Deleted' }
                        },
                        {
                            _or: [
                                { status: 'Shared' },
                                { createdBy: AuthenticationSrv.currentUser.id }
                            ]
                        }
                    ]
                },
                sort: ['-status'],
                loadAll: true
            });

            this.addDashboard = function() {
                var modalInstance = $uibModal.open({
                    templateUrl: 'views/partials/dashboard/create.dialog.html',
                    controller: function($uibModalInstance, statuses, dashboard) {
                        this.dashboard = dashboard;
                        this.statuses = statuses;

                        this.cancel = function() {
                            $uibModalInstance.dismiss();
                        }

                        this.ok = function() {
                            return $uibModalInstance.close(dashboard);
                        }
                    },
                    controllerAs: '$vm',
                    size: 'lg',
                    resolve: {
                        statuses: function() {
                            return ['Private', 'Shared'];
                        },
                        dashboard: function() {
                            return {
                                title: null,
                                description: null,
                                status: 'Private',
                                definition: JSON.stringify(DashboardSrv.defaultDashboard)
                            };
                        }
                    }
                });

                modalInstance.result.then(function(dashboard) {
                    return DashboardSrv.create(dashboard);
                }).then(function(response) {
                    self.dashboards.update();

                    NotificationSrv.log('The dashboard has been successfully created', 'success');
                }).catch(function(err) {
                    if(err && err.status) {
                        NotificationSrv.error('DashboardsCtrl', err.data, err.status);
                    }
                });
            }

            this.deleteDashboard = function(id) {
                DashboardSrv.remove(id)
                    .then(function(response) {
                        self.dashboards.update();
                        
                        NotificationSrv.log('The dashboard has been successfully removed', 'success');
                    });
            }
        });
})();
