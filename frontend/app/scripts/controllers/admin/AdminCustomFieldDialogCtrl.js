(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminCustomFieldDialogCtrl', function($scope, $uibModalInstance, CustomFieldsSrv, NotificationSrv, customField) {
        var self = this;
        self.config = {
            types: [
                'string', 'integer', 'boolean', 'date', 'float'
            ],
            reference: '^[a-zA-Z]{1}[a-zA-Z0-9_-]*'
        };

        self.customField = customField;
        self.customField.options = (customField.options || []).join('\n');

        var onSuccess = function(data) {
            NotificationSrv.log('The Custom field has been successfully saved.', 'success');
            $uibModalInstance.close(data);
        };

        var onFailure = function(response) {
            NotificationSrv.error('AdminCustomFieldDialogCtrl', response.data, response.status);
        };

        var buildOptionsCollection = function(options) {
            if (!options || options === '') {
                return [];
            }

            var type = self.customField.type;
            var values = self.customField.options.split('\n');

            switch(type) {
                case 'integer':
                    return _.without(values.map(function(item) {
                        return Number(item);
                    }), NaN) || [];
                case 'float':
                    return _.without(values.map(function(item) {
                        return parseFloat(item.replace(/,/gi, '.'));
                    }), NaN) || [];
            }

            return values;
        };

        self.saveField = function(form) {
            if (!form.$valid) {
                return;
            }

            var postData = _.pick(self.customField, 'name', 'reference', 'description', 'type', 'mandatory');
            postData.options = buildOptionsCollection(self.customField.options);

            if (self.customField.id) {
                CustomFieldsSrv.update(self.customField.id, postData)
                    .then(onSuccess)
                    .catch(onFailure);
            } else {

                CustomFieldsSrv.get(postData.reference)
                    .then(function() {
                        form.reference.$setValidity('unique', false);
                        form.reference.$setDirty();
                    }, function(err) {
                        if(err.status === 404) {
                            CustomFieldsSrv.create(postData)
                                .then(onSuccess)
                                .catch(onFailure);
                        }
                    })
                    .catch(function(err) {
                        NotificationSrv.error('AdminCustomFieldDialogCtrl', err.data, err.status);
                    });
            }
        };

        self.clearUniqueNameError = function(form) {
            form.reference.$setValidity('unique', true);
            form.reference.$setPristine();
        };

        self.cancel = function() {
            $uibModalInstance.dismiss();
        };

        self.onNamechanged = function(form) {
            if (self.customField.id || !self.customField.name) {
                return;
            }

            var reference = s.trim(s.slugify(self.customField.name));

            self.customField.reference = reference;

            self.clearUniqueNameError(form);
        };

    });
})();
