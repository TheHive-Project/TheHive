(function () {
    'use strict';

    angular.module('theHiveControllers')
        .controller('TaxonomyListCtrl', TaxonomyListCtrl)
        .controller('TaxonomyDialogCtrl', TaxonomyDialogCtrl)
        .controller('TaxonomyImportCtrl', TaxonomyImportCtrl);

    function TaxonomyListCtrl($scope, $uibModal, PaginatedQuerySrv, FilteringSrv, TaxonomySrv, NotificationSrv, ModalSrv, QuerySrv, appConfig) {
        var self = this;

        this.appConfig = appConfig;
        this.allTaxonomyCount = null;

        self.$onInit = function () {
            self.filtering = new FilteringSrv('taxonomy', 'taxonomy.list', {
                version: 'v1',
                defaults: {
                    showFilters: true,
                    showStats: false,
                    pageSize: 15,
                    sort: ['+namespace']
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

            QuerySrv.count(
                'v1',
                [{ '_name': 'listTaxonomy' }],
                {
                    name: 'all-taxonomy'
                })
                .then(function (total) {
                    self.allTaxonomyCount = total;
                });
        };

        self.load = function () {
            this.loading = true;

            this.list = new PaginatedQuerySrv({
                name: 'taxonomies',
                root: undefined,
                objectType: 'taxonomy',
                version: 'v1',
                scope: $scope,
                sort: self.filtering.context.sort,
                loadAll: false,
                pageSize: self.filtering.context.pageSize,
                filter: this.filtering.buildQuery(),
                operations: [
                    { '_name': 'listTaxonomy' }
                ],
                extraData: ['enabled'],
                onUpdate: function () {
                    self.loading = false;
                }
            });
        };

        self.show = function (taxonomy) {
            // var modalInstance = $uibModal.open({

            $uibModal.open({
                animation: true,
                templateUrl: 'views/partials/admin/taxonomy/view.html',
                controller: 'TaxonomyDialogCtrl',
                controllerAs: '$modal',
                size: 'max',
                resolve: {
                    taxonomy: angular.copy(taxonomy)
                }
            });

            // modalInstance.result
            //     .then(function() {
            //         self.load();
            //     })
            //     .catch(function(err){
            //         if(err && !_.isString(err)) {
            //             NotificationSrv.error('Taxonomies import', err.data, err.status);
            //         }
            //     });
        };


        self.import = function () {
            var modalInstance = $uibModal.open({
                animation: true,
                templateUrl: 'views/partials/admin/taxonomy/import.html',
                controller: 'TaxonomyImportCtrl',
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
                        NotificationSrv.error('Taxonomies import', err.data, err.status);
                    }
                });
        };

        this.toggleActive = function (taxonomy) {
            var active = !taxonomy.extraData.enabled;

            TaxonomySrv.toggleActive(taxonomy._id, active)
                .then(function () {
                    NotificationSrv.log(['Taxonomy [', taxonomy.namespace, '] has been successfully', (active ? 'activated' : 'deactivated')].join(' '), 'success');

                    self.load();
                })
                .catch(function (err) {
                    if (err && !_.isString(err)) {
                        NotificationSrv.error('Taxonomies ' + active ? 'activation' : 'deactivation', err.data, err.status);
                    }
                });
        };

        self.remove = function (taxonomy) {
            var modalInstance = ModalSrv.confirm(
                'Remove taxonomy',
                'Are you sure you want to remove the selected taxonomy?', {
                flavor: 'danger',
                okText: 'Yes, remove it'
            }
            );

            modalInstance.result
                .then(function () {
                    return TaxonomySrv.remove(taxonomy._id);
                })
                .then(function ( /*response*/) {
                    self.load();
                    NotificationSrv.success(
                        'Taxonomy ' + taxonomy.namespace + ' has been successfully removed.'
                    );
                })
                .catch(function (err) {
                    if (err && !_.isString(err)) {
                        NotificationSrv.error('TaxonomyListCtrl', err.data, err.status);
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

    }

    function TaxonomyDialogCtrl($uibModalInstance, TaxonomySrv, NotificationSrv, taxonomy) {
        this.taxonomy = taxonomy;

        this.ok = function () {
            $uibModalInstance.close();
        };

        this.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };
    }

    function TaxonomyImportCtrl($uibModalInstance, TaxonomySrv, NotificationSrv, appConfig) {
        this.appConfig = appConfig;
        this.formData = {};
        this.loading = false;

        this.ok = function () {
            this.loading = true;
            TaxonomySrv.import(this.formData)
                .then(function () {
                    $uibModalInstance.close();
                })
                .catch(function (response) {
                    NotificationSrv.error('TaxonomyImportCtrl', response.data, response.status);
                })
                .finally(function () {
                    this.loading = false;
                });
        };

        this.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };
    }
})();
