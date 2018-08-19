(function () {
    'use strict';

    angular.module('theHiveControllers')
        .controller('AdminCaseReportingTemplatesCtrl', AdminCaseReportingTemplatesCtrl)
        .controller('AdminCaseReportingTemplateDialogCtrl', AdminCaseReportingTemplateDialogCtrl)
        .controller('AdminCaseReportingTemplateImportCtrl', AdminCaseReportingTemplateImportCtrl)
        .controller('AdminCaseReportingTemplateMakeDefaultCtrl', AdminCaseReportingTemplateMakeDefaultCtrl)
        .controller('AdminCaseReportingTemplateDeleteCtrl', AdminCaseReportingTemplateDeleteCtrl);

    function AdminCaseReportingTemplatesCtrl($q, $uibModal, CaseReportingTemplateSrv, NotificationSrv){
        var self = this;
        
        this.templates = [];
        this.templateTitles = [];
        this.templateCount = 0;
        
        this.load = function(){
            $q.all([
                CaseReportingTemplateSrv.list()
            ]).then(function (response) {
                self.templates = response[0];
                
                self.templateTitles = [];
                for (var ii = 0; ii < self.templates.length; ++ii) {
                    self.templateTitles.push(self.templates[ii].title);
                }
                self.templateCount = self.templateTitles.length;
                return $q.resolve(self.templates);
            }, function(rejection){
                NotificationSrv.error('AdminCaseReportingTemplatesCtrl', rejection.data, rejection.status);
            })
        };
        
        this.showTemplate = function (template) {
            var modalInstance = $uibModal.open({
                templateUrl: 'views/partials/admin/case-reporting-template-dialog.html',
                controller: 'AdminCaseReportingTemplateDialogCtrl',
                controllerAs: 'vm',
                size: 'max',
                resolve: {
                    template: function () {
                        return template;
                    }
                }
            });

            modalInstance.result.then(function() {
                self.load();
            });
        };
        
        this.deleteTemplate = function(template) {
            var modalInstance = $uibModal.open({
                templateUrl: 'views/partials/admin/case-reporting-template-delete.html',
                controller: 'AdminCaseReportingTemplateDeleteCtrl',
                controllerAs: 'vm',
                size: '',
                resolve: {
                    template: function() {
                        return template;
                    }
                }
            });

            modalInstance.result.then(function() {
                self.load();
            });
        };

        this.makeDefaultTemplate = function(template, templates) {
            var modalInstance = $uibModal.open({
                templateUrl: 'views/partials/admin/case-reporting-template-make-default.html',
                controller: 'AdminCaseReportingTemplateMakeDefaultCtrl',
                controllerAs: 'vm',
                size: '',
                resolve: {
                    template: function() {
                        return template;
                    },
                    templates: function() {
                        return templates;
                    }
                }
            });

            modalInstance.result.then(function() {
                self.load();
            });
        };

        this.import = function () {
            var modalInstance = $uibModal.open({
                animation: true,
                templateUrl: 'views/partials/admin/case-reporting-template-import.html',
                controller: 'AdminCaseReportingTemplateImportCtrl',
                controllerAs: 'vm',
                size: 'lg'
            });

            modalInstance.result.then(function() {
                self.load();
            });
        };

        this.load();
    }


    function AdminCaseReportingTemplateDialogCtrl($uibModalInstance, CaseReportingTemplateSrv, NotificationSrv, template) {
        this.template = template;
        this.editorOptions = {
            useWrapMode: true,
            showGutter: true
        };

        this.formData = _.pick(template, 'id', 'title', 'content');
        
        this.cancel = function () {
            $uibModalInstance.dismiss();
        };

        this.ok = function() {
            this.template.content = this.formData.content;
            CaseReportingTemplateSrv.update(this.template.id, this.template)
                .then(function() {
                    $uibModalInstance.close();
                }, function(response) {
                    NotificationSrv.error('AdminCaseReportingTemplateDialogCtrl', response.data, response.status);
                });
        };
    }

    function AdminCaseReportingTemplateDeleteCtrl($uibModalInstance, CaseReportingTemplateSrv, NotificationSrv, template) {
        this.template = template;

        this.ok = function () {
            CaseReportingTemplateSrv.delete(template.id)
                .then(function() {
                    $uibModalInstance.close();
                }, function(response) {
                    NotificationSrv.error('AdminCaseReportingTemplateDeleteCtrl', response.data, response.status);
                });
        };
        this.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };
    }

    function AdminCaseReportingTemplateMakeDefaultCtrl($uibModalInstance, CaseReportingTemplateSrv, NotificationSrv, template, templates) {
        this.template = template;
        this.templates = templates;

        this.ok = function () {
            for (var ii = 0; ii < this.templates.length; ++ii) {
                this.templates[ii].isDefault = false;
                CaseReportingTemplateSrv.update(this.templates[ii].id, this.templates[ii])
                  .then(function() {
                      
                  }, 
                  function(response) {
                    NotificationSrv.error('AdminCaseReportingTemplateMakeDefaultCtrl', response.data, response.status);
                });
            }
            
            template.isDefault = true;
            CaseReportingTemplateSrv.update(template.id, template)
                .then(function() {
                    $uibModalInstance.close();
                }, function(response) {
                    NotificationSrv.error('AdminCaseReportingTemplateMakeDefaultCtrl', response.data, response.status);
                });
        };
        this.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };
    }

    function AdminCaseReportingTemplateImportCtrl($uibModalInstance, CaseReportingTemplateSrv, NotificationSrv) {
        this.formData = {};

        this.ok = function () {
            CaseReportingTemplateSrv.import(this.formData)
                .then(function() {
                    $uibModalInstance.close();
                }, function(response) {
                    NotificationSrv.error('AdminCaseReportingTemplateImportCtrl', response.data, response.status);
                });
        };

        this.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };
    }
})();
