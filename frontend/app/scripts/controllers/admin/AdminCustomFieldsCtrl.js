(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminCustomFieldsCtrl',
        function($scope, $uibModal, ListSrv, CustomFieldsCacheSrv, NotificationSrv) {
            var self = this;

            self.reference = {
                types: ['string', 'number', 'boolean', 'date']
            };

            self.state = {
                sort: 'name',
                asc: true
            };

            self.sortBy = function(field) {
                if(self.state.sort === field) {
                    self.state.asc = !self.state.asc;
                } else {
                    self.state.sort = field;
                    self.state.asc = true;
                }
            };

            self.customFields = [];

            self.initCustomfields = function() {
                self.formData = {
                    name: null,
                    label: null,
                    description: null,
                    type: null,
                    options: []
                };

                ListSrv.query({
                    'listId': 'custom_fields'
                }, {}, function(response) {

                    self.customFields = _.map(response.toJSON(), function(value, key) {
                        value.id = key;
                        return value;
                    });

                }, function(response) {
                    NotificationSrv.error('AdminCustomfieldsCtrl', response.data, response.status);
                });
            };

            self.showFieldDialog = function(customField) {
                var modalInstance = $uibModal.open({
                    templateUrl: 'views/partials/admin/custom-field-dialog.html',
                    controller: 'AdminCustomFieldDialogCtrl',
                    controllerAs: '$vm',
                    size: 'lg',
                    resolve: {
                        customField: angular.copy(customField) || {}
                    }
                });

                modalInstance.result.then(function(data) {
                    self.initCustomfields();
                    CustomFieldsCacheSrv.clearCache();
                    $scope.$emit('custom-fields:refresh');
                });
            }

            self.initCustomfields();
        });
})();
