(function() {
    'use strict';

    function ModalConfirmCtrl($scope, $uibModalInstance, title, message, config) {
        this.title = title;
        this.message = message;
        this.config = config;

        this.cancel = function() {
            $uibModalInstance.dismiss('cancel');
        };
        this.confirm = function() {
            $uibModalInstance.close('ok');
        };
    }

    angular.module('theHiveServices')
        .service('ModalSrv', function($uibModal) {

            this.confirm = function(title, message, config) {
                return $uibModal.open({
                    controller: ModalConfirmCtrl,
                    templateUrl: 'views/components/common/modal/modal.confirm.html',
                    controllerAs: '$modal',
                    resolve: {
                        title: function() {
                            return title;
                        },
                        message: function() {
                            return message;
                        },
                        config: function() {
                            return config || {};
                        }
                    }
                });
            };

        });
})();
