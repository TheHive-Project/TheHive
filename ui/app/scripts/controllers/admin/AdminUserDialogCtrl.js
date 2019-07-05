(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminUserDialogCtrl', function($scope, $uibModalInstance, UserSrv, NotificationSrv, user, isEdit) {
        var self = this;

        self.user = user;
        self.isEdit = isEdit;

        var formData = _.defaults(_.pick(self.user, 'id', 'name', 'roles'), {
            id: null,
            name: null,
            roles: [],
            alert: false
        });
        formData.alert = formData.roles.indexOf('alert') !== -1;
        formData.roles = _.without(formData.roles, 'alert');

        self.formData = formData;

        var onSuccess = function(data) {
            $uibModalInstance.close(data);
        };

        var onFailure = function(response) {
            NotificationSrv.error('AdminUserDialogCtrl', response.data, response.status);
        };

        var buildRoles = function(roles, alert) {
            var result = angular.copy(roles) || [];

            if(alert && roles.indexOf('alert') === -1) {
                result.push('alert');
            } else if (!alert && roles.indexOf('alert') !== -1) {
                result = _.omit(result, 'alert');
            }

            return result;
        };

        self.saveUser = function(form) {
            if (!form.$valid) {
                return;
            }

            var postData = {};

            if (self.user.id) {
                postData = {
                    name: self.formData.name,
                    roles: buildRoles(self.formData.roles, self.formData.alert)
                };
                UserSrv.update({'userId': self.user.id}, postData, onSuccess, onFailure);
            } else {
                postData = {
                    login: self.formData.id.toLowerCase(),
                    name: self.formData.name,
                    roles: buildRoles(self.formData.roles, self.formData.alert)
                };
                UserSrv.save(postData, onSuccess, onFailure);
            }
        };

        self.cancel = function() {
            $uibModalInstance.dismiss();
        }
    });
})();
