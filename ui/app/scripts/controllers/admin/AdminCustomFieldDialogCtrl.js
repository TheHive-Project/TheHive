(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminCustomFieldDialogCtrl',
        function($scope, $uibModalInstance, ListSrv, NotificationSrv, customField) {
            var self = this;
            self.reference = {
                types: ['string', 'number', 'boolean', 'date']
            };

            self.customField = customField;
            self.customField.options = (customField.options || []).join('\n');

            var onSuccess = function(data) {
                $uibModalInstance.close(data);
            };

            var onFailure = function(response) {
                NotificationSrv.error('AdminCustomFieldDialogCtrl', response.data, response.status);
            };

            var buildOptionsCollection = function(options) {
                if(!options || options === '') {
                    return [];
                }

                var type = self.customField.type;
                //var values = _.isArray(self.customField.options) ? self.customField.options : self.customField.options.split('\n');
                var values = self.customField.options.split('\n');

                if(type === 'number') {                    
                    return _.without(values.map(function(item) {
                        return Number(item);
                    }), NaN);
                }

                return values;
            };

            self.saveField = function() {
                var postData = _.pick(self.customField, 'name', 'title', 'label', 'description', 'type');
                postData.options = buildOptionsCollection(self.customField.options);

                if(self.customField.id) {
                    ListSrv.update(
                        {'itemId': self.customField.id},
                        {'value': JSON.stringify(postData)},
                        onSuccess,
                        onFailure);
                } else {
                    ListSrv.save(
                        {'listId': 'custom_fields'},
                        {'value': JSON.stringify(postData)},
                        onSuccess,
                        onFailure);
                }
            };

            self.cancel = function() {
                $uibModalInstance.dismiss();
            }

        });
})();
