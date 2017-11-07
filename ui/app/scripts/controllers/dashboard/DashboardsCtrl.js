(function() {
    'use strict';

    angular
        .module('theHiveControllers')
        .controller('DashboardModalCtrl', function($uibModalInstance, statuses, dashboard) {
            this.dashboard = dashboard;
            this.statuses = statuses;

            this.cancel = function() {
                $uibModalInstance.dismiss();
            };

            this.ok = function() {
                return $uibModalInstance.close(dashboard);
            };
        })
        .controller('DashboardsCtrl', function($scope, $state, $uibModal, PSearchSrv, ModalUtilsSrv, NotificationSrv, DashboardSrv, AuthenticationSrv) {
            this.dashboards = [];
            var self = this;

            this.load = function() {
                DashboardSrv.list().then(function(response) {
                    self.dashboards = response.data;
                });
            };

            this.load();

            this.openDashboardModal = function(dashboard) {
                return $uibModal.open({
                    templateUrl: 'views/partials/dashboard/create.dialog.html',
                    controller: 'DashboardModalCtrl',
                    controllerAs: '$vm',
                    size: 'lg',
                    resolve: {
                        statuses: function() {
                            return ['Private', 'Shared'];
                        },
                        dashboard: function() {
                            return dashboard;
                        }
                    }
                });
            };

            this.addDashboard = function() {
                var modalInstance = this.openDashboardModal({
                    title: null,
                    description: null,
                    status: 'Private',
                    definition: JSON.stringify(DashboardSrv.defaultDashboard)
                });

                modalInstance.result
                    .then(function(dashboard) {
                        return DashboardSrv.create(dashboard);
                    })
                    .then(function(response) {
                        self.load();

                        NotificationSrv.log('The dashboard has been successfully created', 'success');
                    })
                    .catch(function(err) {
                        if (err && err.status) {
                            NotificationSrv.error('DashboardsCtrl', err.data, err.status);
                        }
                    });
            };

            this.duplicateDashboard = function(dashboard) {
                var copy = _.pick(dashboard, 'title', 'description', 'status', 'definition');
                copy.title = 'Copy of ' + copy.title;

                this.openDashboardModal(copy)
                    .result.then(function(dashboard) {
                        return DashboardSrv.create(dashboard);
                    })
                    .then(function(response) {
                        $state.go('app.dashboards-view', {id: response.data.id});

                        NotificationSrv.log('The dashboard has been successfully created', 'success');
                    })
                    .catch(function(err) {
                        if (err && err.status) {
                            NotificationSrv.error('DashboardsCtrl', err.data, err.status);
                        }
                    });
            };

            this.editDashboard = function(dashboard) {
                var copy = _.extend({}, dashboard);

                this.openDashboardModal(copy).result.then(function(dashboard) {
                    return DashboardSrv.update(dashboard.id, _.omit(dashboard, 'id'));
                })
                .then(function(response) {
                    self.load()

                    NotificationSrv.log('The dashboard has been successfully updated', 'success');
                })
                .catch(function(err) {
                    if (err && err.status) {
                        NotificationSrv.error('DashboardsCtrl', err.data, err.status);
                    }
                });
            };

            this.deleteDashboard = function(id) {
                ModalUtilsSrv.confirm('Remove dashboard', 'Are you sure you want to remove this dashboard', {
                    okText: 'Yes, remove it',
                    flavor: 'danger'
                })
                    .then(function() {
                        return DashboardSrv.remove(id);
                    })
                    .then(function(response) {
                        self.load();

                        NotificationSrv.log('The dashboard has been successfully removed', 'success');
                    });
            };
        });
})();
