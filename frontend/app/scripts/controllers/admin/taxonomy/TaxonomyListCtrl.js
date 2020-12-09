(function() {
    'use strict';

    angular.module('theHiveControllers')
        .controller('TaxonomyListCtrl', TaxonomyListCtrl)
        .controller('TaxonomyImportCtrl', TaxonomyImportCtrl);

    function TaxonomyListCtrl($uibModal, TaxonomySrv, NotificationSrv, ModalSrv, appConfig) {
        var self = this;

        this.appConfig = appConfig;

        self.load = function() {
            TaxonomySrv.list()
                .then(function(response) {
                    self.list = response;
                })
                .catch(function(rejection) {
                    NotificationSrv.error('Taxonomies management', rejection.data, rejection.status);
                });
        };

        self.import = function () {
            var modalInstance = $uibModal.open({
                animation: true,
                templateUrl: 'views/partials/admin/taxonomy/import.html',
                controller: 'TaxonomyImportCtrl',
                controllerAs: '$vm',
                size: 'lg'
            });

            modalInstance.result
                .then(function() {
                    self.load();
                })
                .catch(function(err){
                    if(err && !_.isString(err)) {
                        NotificationSrv.error('Taxonomies import', err.data, err.status);
                    }
                });
        };

        this.toggleActive = function(id, active) {
            TaxonomySrv.toggleActive(id, active)
                .then(function() {
                    NotificationSrv.log('Taxonomy has been successfully ' + active ? 'activated' : 'deactivated', 'success');

                    self.load();
                })
                .catch(function(err){
                    if(err && !_.isString(err)) {
                        NotificationSrv.error('Taxonomies ' + active ? 'activation' : 'deactivation', err.data, err.status);
                    }
                });
        };

        self.update = function(id, taxonomy) {
            // TODO
            // TaxonomySrv.update(id, _.pick(taxonomy, '...'))
            TaxonomySrv.update(id, _.pick(taxonomy, '...'))
                .then(function(/*response*/) {
                    self.load();
                    NotificationSrv.log('Taxonomy updated successfully', 'success');
                })
                .catch(function(err) {
                    NotificationSrv.error('Error', 'Taxonomy update failed', err.status);
                });
        };

        self.create = function(taxonomy) {
            TaxonomySrv.create(taxonomy)
                .then(function(/*response*/) {
                    self.load();
                    NotificationSrv.log('Taxonomy created successfully', 'success');
                })
                .catch(function(err) {
                    NotificationSrv.error('Error', 'Taxonomy creation failed', err.status);
                });
        };

        self.$onInit = function() {
            self.load();
        };
    }

    function TaxonomyImportCtrl($uibModalInstance, TaxonomySrv, NotificationSrv) {
        this.formData = {};

        this.ok = function () {
            TaxonomySrv.import(this.formData)
                .then(function() {
                    $uibModalInstance.close();
                }, function(response) {
                    NotificationSrv.error('TaxonomyImportCtrl', response.data, response.status);
                });
        };

        this.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };
    }
})();
