(function () {
    'use strict';

    angular.module('theHiveControllers')
        .controller('AdminReportTemplatesCtrl', AdminReportTemplatesCtrl)
        .controller('AdminReportTemplateDialogCtrl', AdminReportTemplateDialogCtrl)
        .controller('AdminReportTemplateImportCtrl', AdminReportTemplateImportCtrl)
        .controller('AdminReportTemplateDeleteCtrl', AdminReportTemplateDeleteCtrl);


    function AdminReportTemplatesCtrl($q, $modal, AnalyzerSrv, ReportTemplateSrv) {
        var self = this;

        this.templates = [];
        this.analyzers = [];
        this.analyzerCount = 0;


        this.load = function() {
            $q.all([
                ReportTemplateSrv.list(),
                AnalyzerSrv.query()
            ]).then(function (response) {
                self.templates = response[0].data;
                self.analyzers = response[1];

                return $q.resolve(self.analyzers);
            }).then(function (analyzersMap) {
                if(_.isEmpty(analyzersMap)) {
                    _.each(_.pluck(self.templates, 'analyzers'), function(item) {
                        analyzersMap[item] = {
                            id: item
                        };
                    });
                }

                _.each(self.templates, function (tpl) {
                    analyzersMap[tpl.analyzerId][tpl.reportType + 'Report'] = tpl;
                });

                self.analyzerCount = _.keys(analyzersMap).length;
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

        this.deleteTemplate = function(template) {
            var modalInstance = $modal.open({
                templateUrl: 'views/partials/admin/report-template-delete.html',
                controller: 'AdminReportTemplateDeleteCtrl',
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

        this.import = function () {
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
    }

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

        this.formData = _.pick(reportTemplate, 'id', 'reportType', 'content');
        this.formData.analyzerId = this.analyzer.id;

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

    function AdminReportTemplateDeleteCtrl($modalInstance, ReportTemplateSrv, AlertSrv, template) {
        this.template = template;

        this.ok = function () {
            ReportTemplateSrv.delete(template.id)
                .then(function() {
                    $modalInstance.close();
                }, function(response) {
                    AlertSrv.error('AdminReportTemplateDeleteCtrl', response.data, response.status);
                });
        };
        this.cancel = function () {
            $modalInstance.dismiss('cancel');
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
