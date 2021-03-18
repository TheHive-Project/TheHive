(function() {
    'use strict';

    angular
        .module('theHiveControllers')
        .controller('DashboardImportCtrl', function($scope, $uibModalInstance) {
            var self = this;
            this.formData = {
                fileContent: {}
            };

            $scope.$watch('vm.formData.attachment', function(file) {
                if(!file) {
                    self.formData.fileContent = {};
                    return;
                }
                var aReader = new FileReader();
                aReader.readAsText(self.formData.attachment, 'UTF-8');
                aReader.onload = function (evt) {
                    $scope.$apply(function() {
                        self.formData.fileContent = JSON.parse(aReader.result);
                    });
                }
                aReader.onerror = function (evt) {
                    $scope.$apply(function() {
                        self.formData.fileContent = {};
                    });
                }
            });

            this.ok = function () {
                var dashboard = _.pick(this.formData.fileContent, 'title', 'description', 'status');
                dashboard.definition = JSON.stringify(this.formData.fileContent.definition || {});

                $uibModalInstance.close(dashboard);
            };

            this.cancel = function () {
                $uibModalInstance.dismiss('cancel');
            };
        })
        .controller('DashboardModalCtrl', function($uibModalInstance, $state, statuses, dashboard) {
            this.dashboard = dashboard;
            this.statuses = statuses;

            this.cancel = function() {
                $uibModalInstance.dismiss();
            };

            this.ok = function() {
                return $uibModalInstance.close(dashboard);
            };
        })
        .controller('DashboardsCtrl', function($scope, $state, $uibModal, PaginatedQuerySrv, FilteringSrv, ModalUtilsSrv, NotificationSrv, DashboardSrv, AuthenticationSrv) {
            this.dashboards = [];
            var self = this;

            this.$onInit = function() {
                self.filtering = new FilteringSrv('dashboard', 'dashboard.list', {
                    version: 'v0',
                    defaults: {
                        showFilters: true,
                        showStats: false,
                        pageSize: 15,
                        sort: ['+title']
                    },
                    defaultFilter: []
                });

                self.filtering.initContext('list')
                    .then(function() {
                        self.load();

                        $scope.$watch('$vm.list.pageSize', function (newValue) {
                            self.filtering.setPageSize(newValue);
                        });
                    });
            }

            this.load = function() {

                self.list = new PaginatedQuerySrv({
                    name: 'dashboard-list',
                    version: 'v0',
                    skipStream: true,
                    sort: self.filtering.context.sort,
                    loadAll: false,
                    pageSize: self.filtering.context.pageSize,
                    filter: this.filtering.buildQuery(),
                    operations: [
                        {'_name': 'listDashboard'}
                    ],
                    onFailure: function(err) {
                        if(err && err.status === 400) {
                            self.filtering.resetContext();
                            self.load();
                        }
                    }
                });
            };

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
                        $state.go('app.dashboards-view', {id: response.data.id});

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

            this.exportDashboard = function(dashboard) {
                DashboardSrv.exportDashboard(dashboard);
            }

            this.importDashboard = function() {
                var modalInstance = $uibModal.open({
                    animation: true,
                    templateUrl: 'views/partials/dashboard/import.dialog.html',
                    controller: 'DashboardImportCtrl',
                    controllerAs: 'vm',
                    size: 'lg'
                });

                modalInstance.result.then(function(dashboard) {
                    return DashboardSrv.create(dashboard);
                })
                .then(function(response) {
                    $state.go('app.dashboards-view', {id: response.data.id});

                    NotificationSrv.log('The dashboard has been successfully imported', 'success');
                })
                .catch(function(err) {
                    if (err && err.status) {
                        NotificationSrv.error('DashboardsCtrl', err.data, err.status);
                    }
                });
            }

            // Filtering
            this.toggleFilters = function () {
                this.filtering.toggleFilters();
            };

            this.filter = function () {
                self.filtering.filter().then(this.applyFilters);
            };

            this.clearFilters = function () {
                this.filtering.clearFilters()
                    .then(self.search);
            };

            this.removeFilter = function (index) {
                self.filtering.removeFilter(index)
                    .then(self.search);
            };

            this.search = function () {
                self.load();
                self.filtering.storeContext();
            };
            this.addFilterValue = function (field, value) {
                this.filtering.addFilterValue(field, value);
                this.search();
            };

            this.filterBy = function(field, value) {
                self.filtering.clearFilters()
                    .then(function(){
                        self.addFilterValue(field, value);
                    });
            };

            this.sortBy = function(sort) {
                self.list.sort = sort;
                self.list.update();
                self.filtering.setSort(sort);
            };

            this.sortByField = function(field) {
                var context = this.filtering.context;
                var currentSort = Array.isArray(context.sort) ? context.sort[0] : context.sort;
                var sort = null;

                if(currentSort.substr(1) !== field) {
                    sort = ['+' + field];
                } else {
                    sort = [(currentSort === '+' + field) ? '-'+field : '+'+field];
                }

                self.list.sort = sort;
                self.list.update();
                self.filtering.setSort(sort);
            };
        });
})();
