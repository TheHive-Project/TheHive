(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseTemplatesDialogCtrl',
        function($scope, $uibModalInstance, templates) {
            this.templates = templates;
            this.state = {
                filter: null,
                selected: null
            };

            this.selectTemplate = function(template) {
                if(this.state.selected && this.state.selected.id === template.id) {
                    this.state.selected = null;
                } else {
                    this.state.selected = template;
                }
            };

            this.next = function(template) {
                $uibModalInstance.close(template);
            }

            this.cancel = function() {
                $uibModalInstance.dismiss();
            };
        }
    );
})();
