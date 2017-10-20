(function() {
    'use strict';
    angular.module('theHiveServices').service('ModalUtilsSrv', function($uibModal) {

        this.confirm = function(title, message, config) {
            var modal = $uibModal.open({
                templateUrl: 'views/partials/utils/confirm.modal.html',
                controller: function($uibModalInstance, title, message, config) {
                    this.title = title;
                    this.message = message;
                    this.config = config;
                    
                    this.cancel = function() {
                        $uibModalInstance.dismiss();
                    }
                    this.confirm = function() {
                        $uibModalInstance.close();
                    }
                },
                controllerAs: '$modal',
                resolve: {
                    title: function() {
                        return title;
                    },
                    message: function() {
                        return message
                    },
                    config: function() {
                        return config || {};
                    }
                }
            });

            return modal.result;
        }
    });
})();
