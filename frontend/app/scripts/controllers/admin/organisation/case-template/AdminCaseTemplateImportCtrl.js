(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminCaseTemplateImportCtrl', function($scope, $uibModalInstance) {
        var self = this;
        this.formData = {
            fileContent: {}
        };

        $scope.$watch('vm.formData.attachment', function(file) {
            if (!file) {
                self.formData.fileContent = {};
                return;
            }
            var aReader = new FileReader();
            aReader.readAsText(self.formData.attachment, 'UTF-8');
            aReader.onload = function( /*evt*/ ) {
                $scope.$apply(function() {
                    self.formData.fileContent = JSON.parse(aReader.result);
                });
            };
            aReader.onerror = function( /*evt*/ ) {
                $scope.$apply(function() {
                    self.formData.fileContent = {};
                });
            };
        });

        this.ok = function() {
            var template = _.pick(
                this.formData.fileContent,
                'name',
                'title',
                'description',
                'tlp',
                'pap',
                'severity',
                'tags',
                'status',
                'titlePrefix',
                'tasks',
                'customFields'
            );
            $uibModalInstance.close(template);
        };

        this.cancel = function() {
            $uibModalInstance.dismiss('cancel');
        };
    });
})();
