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

            self.saveField = function() {
                var postData = _.pick(self.customField, 'name', 'title', 'label', 'description', 'type');
                postData.options = _.isArray(self.customField.options) ? self.customField.options : self.customField.options.split('\n');

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
