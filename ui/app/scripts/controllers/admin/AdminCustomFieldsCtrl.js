(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminCustomFieldsCtrl',
        function($scope, ListSrv, CustomFieldsCacheSrv, NotificationSrv) {
            var self = this;

            self.reference = {
                types: ['string', 'number', 'boolean', 'date']
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

                    self.customFields = _.values(response).filter(_.isString).map(function(item) {
                        return JSON.parse(item);
                    });

                }, function(response) {
                    NotificationSrv.error('AdminCustomfieldsCtrl', response.data, response.status);
                });
            };

            self.addCustomField = function() {
                ListSrv.save({
                        'listId': 'custom_fields'
                    }, {
                        'value': JSON.stringify(self.formData)
                    }, function() {
                        self.initCustomfields();

                        CustomFieldsCacheSrv.clearCache();

                        $scope.$emit('custom-fields:refresh');
                    },
                    function(response) {
                        NotificationSrv.error('AdminCustomfieldsCtrl', response.data, response.status);
                    });
            };

            self.initCustomfields();
        });
})();
