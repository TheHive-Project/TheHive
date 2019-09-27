(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminCustomFieldDialogCtrl', function($scope, $uibModalInstance, CustomFieldsSrv, NotificationSrv, customField) {
        var self = this;
        self.config = {
            types: [
                'string', 'integer', 'boolean', 'date', 'float'
            ],
            namePattern: '^[a-zA-Z]{1}[a-zA-Z0-9_-]*'
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
            if (!options || options === '') {
                return [];
            }

            var type = self.customField.type;
            var values = self.customField.options.split('\n');

            if (type === 'integer' || type === 'float') {
                return _.without(values.map(function(item) {
                    return Number(item);
                }), NaN) || [];
            }

            return values;
        };

        self.saveField = function(form) {
            if (!form.$valid) {
                return;
            }

            var postData = _.pick(self.customField, 'name', 'displayName', 'description', 'type', 'mandatory');
            postData.options = buildOptionsCollection(self.customField.options);

            if (self.customField.id) {
                CustomFieldsSrv.update(self.customField.id, postData)
                    .then(onSuccess)
                    .catch(onFailure);
                // ListSrv.update({
                //     'itemId': self.customField.id
                // }, {
                //     'value': postData
                // }, onSuccess, onFailure);
            } else {

                CustomFieldsSrv.create(postData)
                    .then(onSuccess)
                    .catch(onFailure);

                // ListSrv.exists({
                //     'listId': 'custom_fields'
                // }, {
                //     key: 'reference',
                //     value: postData.reference
                // }, function(response) {
                //
                //     if (response.toJSON().found === true) {
                //         form.name.$setValidity('unique', false);
                //         form.name.$setDirty();
                //     } else {
                //         ListSrv.save({
                //             'listId': 'custom_fields'
                //         }, {
                //             'value': postData
                //         }, onSuccess, onFailure);
                //     }
                // }, onFailure);
            }
        };

        self.clearUniqueNameError = function(form) {
            form.name.$setValidity('unique', true);
            form.name.$setPristine();
        };

        self.cancel = function() {
            $uibModalInstance.dismiss();
        };

        self.onNamechanged = function(form) {
            if (!self.customField.displayName) {
                return;
            }

            var name = s.trim(s.slugify(self.customField.displayName));
            name = name.charAt(0).toLowerCase() + name.slice(1);

            self.customField.name = name;

            self.clearUniqueNameError(form);
        };

    });
})();
