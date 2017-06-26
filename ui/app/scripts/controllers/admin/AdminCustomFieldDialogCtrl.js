(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminCustomFieldDialogCtrl',
        function($scope, $uibModalInstance, ListSrv, NotificationSrv, customField) {
            var self = this;
            self.config = {
                types: ['string', 'number', 'boolean', 'date'],
                referencePattern: '^[a-zA-Z]{1}[a-zA-Z0-9_-]*'
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
                var values = self.customField.options.split('\n');

                if(type === 'number') {
                    return _.without(values.map(function(item) {
                        return Number(item);
                    }), NaN);
                }

                return values;
            };

            self.saveField = function(form) {
                if (!form.$valid) {
                    return;
                }

                var postData = _.pick(self.customField, 'name', 'reference', 'description', 'type');
                postData.options = buildOptionsCollection(self.customField.options);

                if(self.customField.id) {
                    ListSrv.update(
                        {'itemId': self.customField.id},
                        {'value': JSON.stringify(postData)},
                        onSuccess,
                        onFailure);
                } else {
                    ListSrv.exists(
                        {'listId': 'custom_fields'},
                        {
                            key: 'reference',
                            value: postData.reference
                        },
                        function(response) {
                            if(response.data) {
                                ListSrv.save(
                                    {'listId': 'custom_fields'},
                                    {'value': JSON.stringify(postData)},
                                    onSuccess,
                                    onFailure);
                            } else {
                                // TODO handle field validation
                                form.reference.$setValidity('unique', false);
                                form.reference.$setDirty();
                                NotificationSrv.error('AdminCustomFieldDialogCtrl', 'There is already a custom field with the specified reference: ' + postData.reference);
                            }
                        },
                        onFailure
                    )
                }
            };

            self.clearUniqueReferenceError = function(form) {
                form.reference.$setValidity('unique', true);
                form.reference.$setPristine();
            }

            self.cancel = function() {
                $uibModalInstance.dismiss();
            }

            self.onNamechanged = function(form) {
                if(!self.customField.name) {
                    return;
                }

                var reference = s.trim(s.classify(self.customField.name));
                reference = reference.charAt(0).toLowerCase() + reference.slice(1);

                self.customField.reference = reference;

                self.clearUniqueReferenceError(form);
            };

        });
})();
