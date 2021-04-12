(function () {
    'use strict';

    angular.module('theHiveControllers')
        .controller('AttackPatternListCtrl', AttackPatternListCtrl)
        .controller('AttackPatternDialogCtrl', AttackPatternDialogCtrl)
        .controller('AttackPatternImportCtrl', AttackPatternImportCtrl);

    function AttackPatternListCtrl($scope, $uibModal, PaginatedQuerySrv, FilteringSrv, AttackPatternSrv, NotificationSrv, ModalSrv, appConfig) {
        var self = this;

        this.appConfig = appConfig;

        self.load = function () {
            this.loading = true;

            this.list = new PaginatedQuerySrv({
                name: 'attack-patterns',
                root: undefined,
                objectType: 'pattern',
                version: 'v1',
                scope: $scope,
                sort: self.filtering.context.sort,
                loadAll: false,
                pageSize: self.filtering.context.pageSize,
                filter: this.filtering.buildQuery(),
                baseFilter: {
                    _field: 'patternType',
                    _value: 'attack-pattern'
                },
                operations: [
                    { '_name': 'listPattern' }
                ],
                extraData: ['enabled', 'parent'],
                onUpdate: function () {
                    self.loading = false;
                }
            });
        };

        self.show = function (patternId) {
            $uibModal.open({
                animation: true,
                templateUrl: 'views/partials/admin/attack/view.html',
                controller: 'AttackPatternDialogCtrl',
                controllerAs: '$modal',
                size: 'max',
                resolve: {
                    pattern: function () {
                        return AttackPatternSrv.get(patternId);
                    }
                }
            });
        };


        self.import = function () {
            var modalInstance = $uibModal.open({
                animation: true,
                templateUrl: 'views/partials/admin/attack/import.html',
                controller: 'AttackPatternImportCtrl',
                controllerAs: '$vm',
                size: 'lg',
                resolve: {
                    appConfig: self.appConfig
                }
            });

            modalInstance.result
                .then(function () {
                    self.load();
                })
                .catch(function (err) {
                    if (err && !_.isString(err)) {
                        NotificationSrv.error('Pattern import', err.data, err.status);
                    }
                });
        };

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

        self.$onInit = function () {
            self.filtering = new FilteringSrv('pattern', 'attack-pattern.list', {
                version: 'v1',
                defaults: {
                    showFilters: true,
                    showStats: false,
                    pageSize: 15,
                    sort: ['+name']
                },
                defaultFilter: []
            });

            self.filtering.initContext('list')
                .then(function () {
                    self.load();

                    $scope.$watch('$vm.list.pageSize', function (newValue) {
                        self.filtering.setPageSize(newValue);
                    });
                });
        };
    }

    function AttackPatternDialogCtrl($uibModalInstance, AttackPatternSrv, NotificationSrv, pattern) {
        this.pattern = pattern;

        this.ok = function () {
            $uibModalInstance.close();
        };

        this.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };

        this.$onInit = function () {
            if (this.pattern.extraData.parent) {
                this.pattern.isSubTechnique = true;
                this.pattern.parentId = this.pattern.extraData.parent.patternId;
                this.pattern.parentName = this.pattern.extraData.parent.name;
            } else {
                this.pattern.isSubTechnique = false;
            }
        };
    }

    function AttackPatternImportCtrl($uibModalInstance, AttackPatternSrv, NotificationSrv, appConfig) {
        this.appConfig = appConfig;
        this.formData = {};
        this.loading = false;

        this.ok = function () {
            this.loading = true;
            AttackPatternSrv.import(this.formData)
                .then(function () {
                    $uibModalInstance.close();
                })
                .catch(function (response) {
                    NotificationSrv.error('AttackPatternImportCtrl', response.data, response.status);
                })
                .fincally(function () {
                    this.loading = false;
                });
        };

        this.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };
    }
})();
