(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseTemplatesDialogCtrl',
        function($scope, $uibModalInstance, UiSettingsSrv, templates, uiSettings) {
            this.templates = templates;
            this.uiSettings = uiSettings;
            this.state = {
                filter: null,
                selected: null,
                hideEmptyCaseButton: UiSettingsSrv.hideEmptyCaseButton()
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
            };

            this.cancel = function() {
                $uibModalInstance.dismiss();
            };
        }
    );
})();
