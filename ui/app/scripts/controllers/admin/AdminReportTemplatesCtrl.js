(function () {
    'use strict';

    angular.module('theHiveControllers')
        .controller('AdminReportTemplatesCtrl', AdminReportTemplatesCtrl)
        .controller('AdminReportTemplateDialogCtrl', AdminReportTemplateDialogCtrl)
        .controller('AdminReportTemplateImportCtrl', AdminReportTemplateImportCtrl);


    function AdminReportTemplatesCtrl($q, $modal, AnalyzerSrv, ReportTemplateSrv) {
        var self = this;

        this.templates = [];
        this.analyzers = [];


        this.load = function() {
            $q.all([
                ReportTemplateSrv.list(),
                AnalyzerSrv.query()
            ]).then(function (response) {
                self.templates = response[0].data;
                self.analyzers = response[1];
                
                return $q.resolve(self.analyzers);
            }).then(function (analyzersMap) {
                _.each(self.templates, function (tpl) {
                    analyzersMap[tpl.analyzers][tpl.flavor + 'Report'] = tpl;
                });

                console.log(self.analyzers);
            });
        };

        this.showTemplate = function (reportTemplate, analyzer) {
            var modalInstance = $modal.open({
                //scope: $scope,
                templateUrl: 'views/partials/admin/report-template-dialog.html',
                controller: 'AdminReportTemplateDialogCtrl',
                controllerAs: 'vm',
                size: 'max',
                resolve: {
                    reportTemplate: function () {
                        return reportTemplate;
                    },
                    analyzer: function () {
                        return analyzer;
                    }
                }
            });

            modalInstance.result.then(function() {
                self.load();
            });
        };

        this.import = function (analyzer, dataType) {
            var modalInstance = $modal.open({
                animation: true,
                templateUrl: 'views/partials/admin/report-template-import.html',
                controller: 'AdminReportTemplateImportCtrl',
                controllerAs: 'vm',
                size: 'lg'
            });

            modalInstance.result.then(function() {
                self.load();
            });
        };

        this.load();
    };

    function AdminReportTemplateDialogCtrl($modalInstance, reportTemplate, ReportTemplateSrv, AlertSrv, analyzer) {
        this.reportTemplate = reportTemplate;
        this.analyzer = analyzer;
        this.reportTypes = ['short', 'long'];
        this.editorOptions = {
            useWrapMode: true,
            showGutter: true,
            theme: 'default',
            mode: 'xml'
        };    

        this.formData = _.pick(reportTemplate, 'id', 'flavor', 'content');
        this.formData.analyzers = this.analyzer.id;

        this.cancel = function () {
            $modalInstance.dismiss();
        };

        this.saveTemplate = function() {
            ReportTemplateSrv.save(this.formData)
                .then(function() {
                    $modalInstance.close();
                }, function(response) {
                    AlertSrv.error('AdminReportTemplateDialogCtrl', response.data, response.status);
                });
        };

        
    }

    function AdminReportTemplateImportCtrl($modalInstance, ReportTemplateSrv, AlertSrv) {
        this.formData = {};

        this.ok = function () {
            ReportTemplateSrv.import(this.formData)
                .then(function() {
                    $modalInstance.close();
                }, function(response) {
                    AlertSrv.error('AdminReportTemplateImportCtrl', response.data, response.status);
                });
        };

        this.cancel = function () {
            $modalInstance.dismiss('cancel');
        };
    }
})();
